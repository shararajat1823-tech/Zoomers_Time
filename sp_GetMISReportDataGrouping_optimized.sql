/* =====================================================================================
   sp_GetMISReportDataGrouping  -- PERFORMANCE-OPTIMIZED VERSION

   Goal of this rewrite: reduce execution time and CPU WITHOUT changing the result set.

   What changed (and why it is safe):
   ---------------------------------------------------------------------------------
   1. SARGable date filters (BIGGEST WIN).
      Every "CAST(col AS DATE) >= @FromDate AND CAST(col AS DATE) < @ToDateExclusive"
      was replaced with "col >= @FromDate AND col < @ToDateExclusive".
      Because @FromDate / @ToDateExclusive are DATE values (midnight) and the range is
      half-open, the two forms return identical rows -- but CASTing the COLUMN makes the
      predicate non-SARGable, forcing an index/table SCAN and a per-row date conversion
      on millions of rows. The uncast form lets SQL Server SEEK a supporting index and
      skips the conversion entirely. This is the main driver of the high CPU / long runtime.

   2. Fixed illegal alias-qualified target columns in the INSERT column lists
      (".. ,t.Datasource)" / ".. ,f.Datasource)") -> plain "Datasource".
      A target column list may not be alias-qualified; this is a correctness/parse fix.

   3. Added covering-index recommendations (see block at the very top). These are the
      other half of the win -- SARGable predicates only help if an index exists to seek.
      Create them once (DBA task); they are commented out here so the proc still deploys
      on a DB where they already exist.

   Deliberately NOT changed (to preserve results exactly):
      - UNION (not UNION ALL) in the Withdraw / Rejected / TDR blocks: the UNION is doing
        intentional de-duplication across multiple bank-process rows per application.
        Switching to UNION ALL would change the counts.
      - OPTION (RECOMPILE): correct choice for a proc with many optional parameters --
        it lets the optimizer build a plan tailored to the supplied filters each run.
      - All join logic and the metric definitions.

   FURTHER OPTIONS (higher effort, require testing -- see notes at the bottom):
      - Materialize t_ApplicantData(+t_BankProcess) and the Sec-loan equivalents ONCE into
        parameter-filtered temp tables, then drive every metric from those, instead of
        re-scanning the base tables ~16 times.
      - Review the "t_SecLoanBankProcess b JOIN t_ApplicantData a ON b.SecLoanApplID = a.ApplID"
        joins (Sanctioned / MMDisb / PendForMMDisb second-loan parts): they appear to join a
        second-loan id to a first-loan id and may be a latent correctness bug.
   =====================================================================================

   -- RECOMMENDED SUPPORTING INDEXES (create once, outside this proc; review with DBA):
   --   t_ApplicantData    : (FinalSubDate) INCLUDE (ApplID, StateID, DistID, Gender, SocialCatID, SchemeID, AgencyCode, UnitLocation, FinBankID1, FinBankID2, IsUnderAlternativeBank, IsFinalSub, DataSource, SchemeWrkFlowID, AgencyOffID, State_Name, DistrictName, TotalProjectCost, SchemeDetID)
   --   t_ApplicantData    : (BankForwardDate), (WithdrawDate), (ModifyOn)
   --   t_BankProcess      : (SancDate) INCLUDE (ApplID), (MMClaimDt) INCLUDE (ApplID, MMClaimed, MMTrxfDate), (MMTrxfDate) INCLUDE (ApplID), (TDRDepDate, MMTrxfDate) INCLUDE (ApplID)
   --   t_SecLoanApplData  : (FinalSubDate), (BankForwardDate), (ModifyOn), (ApplID, ApplCode)
   --   t_SecLoanBankProcess:(SancDate), (MMClaimDt), (MMTrxfDate), (TDRDepDate, MMTrxfDate) INCLUDE (SecLoanApplID)
   --   t_EdpTraining      : (ApplID) INCLUDE (TrainingMode, EdpCentreID, CertIssueDate)
   --   m_AgencyOffDetail  : (AgencyOffID, AgencyOffStateId)
*/

CREATE OR ALTER PROCEDURE [dbo].[sp_GetMISReportDataGrouping]
@LoanType    VARCHAR(50),
@Level       VARCHAR(50),        -- ZoneWise | StateWise | DistrictWise
@ZoneCode    VARCHAR(10) = NULL, -- filter for StateWise (show states of one zone)
@StateID     INT = NULL,         -- filter for DistrictWise (show districts of one state)
@DistID      INT = NULL,
@FromDate    DATE = NULL,
@ToDate      DATE = NULL,
@Gender      VARCHAR(10) = NULL,
@SocialCatID VARCHAR(100) = NULL,
@SchemeID    INT = NULL,
@AgencyCode  VARCHAR(10) = NULL,
@BankID      INT = NULL,
@LocType     VARCHAR(20) = NULL,
@ApplStatus  VARCHAR(300) = NULL
AS
-- LEVEL 1: Zone -> Agency        EXEC sp_GetMISReportDataGrouping 'Both','ZoneWise',NULL,NULL,NULL,'2025-04-01','2026-03-31'
-- LEVEL 2: Zone -> State         EXEC sp_GetMISReportDataGrouping 'Both','StateWise','CZ',NULL,NULL,'2025-04-01','2026-03-31'
-- LEVEL 3: State -> District     EXEC sp_GetMISReportDataGrouping 'Both','DistrictWise',NULL,33,NULL,'2025-04-01','2026-03-31'

BEGIN
BEGIN TRY
 SET NOCOUNT ON;

 SET @Gender      = NULLIF(@Gender,'');
 SET @LocType     = NULLIF(@LocType,'');
 SET @AgencyCode  = NULLIF(@AgencyCode,'');
 SET @SocialCatID = NULLIF(@SocialCatID,'');
 SET @ApplStatus  = NULLIF(@ApplStatus,'');
 SET @ZoneCode    = NULLIF(@ZoneCode,'');

 IF OBJECT_ID('tempdb..#AgencyZoneMap') IS NOT NULL DROP TABLE #AgencyZoneMap
 IF OBJECT_ID('tempdb..#LoanData')      IS NOT NULL DROP TABLE #LoanData
 IF OBJECT_ID('tempdb..#WorkFlowLevel') IS NOT NULL DROP TABLE #WorkFlowLevel
 IF OBJECT_ID('tempdb..#Metrics')       IS NOT NULL DROP TABLE #Metrics

 DECLARE @BankIDFilter INT = ISNULL(@BankID,0);
 DECLARE @ToDateExclusive DATE = CASE WHEN @ToDate IS NULL THEN NULL ELSE DATEADD(DAY,1,@ToDate) END;

 -- =========================================================
 -- Agency Office -> Zone map, built ONCE and reused everywhere below.
 -- =========================================================
 SELECT ad.AgencyOffID, ms.Zone_Code AS AgencyZoneCode
 INTO #AgencyZoneMap
 FROM m_AgencyOffDetail ad WITH (NOLOCK)
 INNER JOIN m_States ms WITH (NOLOCK) ON ad.AgencyOffStateId = ms.State_ID
 GROUP BY ad.AgencyOffID, ms.Zone_Code;

 CREATE CLUSTERED INDEX IX_AgencyZoneMap ON #AgencyZoneMap(AgencyOffID);

 -- =========================================================
 -- #LoanData: fine-grain keys on every row, re-aggregated at the end.
 -- =========================================================
 CREATE TABLE #LoanData(
  ApplID int,
  ApplCode varchar(100),
  SecLoanApplID int,
  SecApplCode varchar(100),
  LoanType varchar(20),
  ZoneCode varchar(10),
  StateName varchar(100),
  DistrictName varchar(100),
  AgencyCode varchar(10),
  SchemeWrkFlowID int,
  SchemeDetID int,
  TotalProjectCost decimal(18,2),
  isRefferedImplementingOrBankByValidation bit,
  SchemeID int,
  Datasource nvarchar(20)
 )

 IF (@LoanType = 'First' OR @LoanType = 'Both')
 BEGIN
  INSERT INTO #LoanData (ApplID,ApplCode,SecLoanApplID,SecApplCode,LoanType,ZoneCode,StateName,DistrictName,AgencyCode,SchemeWrkFlowID,TotalProjectCost,isRefferedImplementingOrBankByValidation,SchemeID,SchemeDetID,Datasource)
  SELECT t.ApplID, t.ApplCode, 0, '', 'First',
      az.AgencyZoneCode, t.State_Name, t.DistrictName, t.AgencyCode,
      t.SchemeWrkFlowID, t.TotalProjectCost, t.isRefferedImplementingOrBankByValidation, t.SchemeID, t.SchemeDetID, t.Datasource
  FROM t_ApplicantData t WITH (NOLOCK)
  LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = t.AgencyOffID
  WHERE t.IsFinalSub = 1 AND (t.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(t.Datasource)), '') IS NULL)
  AND (@StateID IS NULL OR t.StateID = @StateID)
  AND (@DistID IS NULL OR t.DistID = @DistID)
  AND (@Gender IS NULL OR t.Gender = @Gender)
  AND (@SocialCatID IS NULL OR t.SocialCatID = @SocialCatID)
  AND (@SchemeID IS NULL OR t.SchemeID = @SchemeID)
  AND (@AgencyCode IS NULL OR t.AgencyCode = @AgencyCode)
  AND (@LocType IS NULL OR t.UnitLocation = @LocType)
  AND (@FromDate IS NULL OR t.FinalSubDate >= @FromDate)
  AND (@ToDateExclusive IS NULL OR t.FinalSubDate < @ToDateExclusive)
  AND (
    @BankIDFilter = 0
    OR (ISNULL(t.IsUnderAlternativeBank,0) = 1 AND ISNULL(t.FinBankID2,0) <> 0 AND t.FinBankID2 = @BankIDFilter)
    OR ((ISNULL(t.IsUnderAlternativeBank,0) <> 1 OR ISNULL(t.FinBankID2,0) = 0) AND t.FinBankID1 = @BankIDFilter)
   )
  OPTION (RECOMPILE);
 END

 IF (@LoanType = 'Second' OR @LoanType = 'Both')
 BEGIN
  INSERT INTO #LoanData (ApplID,ApplCode,SecLoanApplID,SecApplCode,LoanType,ZoneCode,StateName,DistrictName,AgencyCode,SchemeWrkFlowID,TotalProjectCost,isRefferedImplementingOrBankByValidation,SchemeID,SchemeDetID,Datasource)
  SELECT S.ApplID, S.SecApplCode, S.SecLoanApplID, S.SecApplCode, 'Second',
      az.AgencyZoneCode, S.State_Name, S.DistrictName, S.AgencyCode,
      S.SchemeWrkFlowID, S.TotalProjectCost, F.isRefferedImplementingOrBankByValidation, S.SchemeID, S.SchemeDetID, S.Datasource
  FROM t_SecLoanApplData S WITH (NOLOCK)
  INNER JOIN t_ApplicantData F WITH (NOLOCK) ON S.ApplCode = F.ApplCode AND F.ApplID = S.ApplID
  LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = F.AgencyOffID
  WHERE S.IsFinalSub = 1
  AND (@StateID IS NULL OR S.StateID = @StateID)
  AND (@DistID IS NULL OR S.DistID = @DistID)
  AND (@Gender IS NULL OR F.Gender = @Gender)
  AND (@SocialCatID IS NULL OR S.SocialCatID = @SocialCatID)
  AND (@SchemeID IS NULL OR S.SchemeID = @SchemeID)
  AND (@AgencyCode IS NULL OR S.AgencyCode = @AgencyCode)
  AND (@LocType IS NULL OR S.UnitLocation = @LocType)
  AND (@FromDate IS NULL OR S.FinalSubDate >= @FromDate)
  AND (@ToDateExclusive IS NULL OR S.FinalSubDate < @ToDateExclusive)
  AND (@BankIDFilter = 0 OR F.FinBankID1 = @BankIDFilter)
  OPTION (RECOMPILE);
 END

 SELECT SchemeWrkFlowID, StageStatus, Seq INTO #WorkFlowLevel FROM m_SchemeWrkFlow WITH (NOLOCK);

 CREATE CLUSTERED INDEX IX_LoanData ON #LoanData(ZoneCode, StateName, DistrictName, AgencyCode, SchemeWrkFlowID);
 CREATE NONCLUSTERED INDEX IX_LoanData_Appl ON #LoanData(ApplID, LoanType) INCLUDE(ZoneCode, StateName, DistrictName, AgencyCode);
 CREATE CLUSTERED INDEX IX_WorkFlow ON #WorkFlowLevel(SchemeWrkFlowID);
 CREATE NONCLUSTERED INDEX IX_WorkFlowStage ON #WorkFlowLevel(StageStatus, Seq) INCLUDE(SchemeWrkFlowID);

 -- =========================================================
 -- #Metrics: one row per fine-grain key, all metrics default 0.
 -- =========================================================
 SELECT DISTINCT ZoneCode, StateName, DistrictName, AgencyCode
 INTO #Metrics
 FROM #LoanData
 WHERE ZoneCode IS NOT NULL OR StateName IS NOT NULL OR DistrictName IS NOT NULL OR AgencyCode IS NOT NULL;

 ALTER TABLE #Metrics ADD
  ApplReceivedCount INT NOT NULL DEFAULT 0,
  ApplReturnedCount INT NOT NULL DEFAULT 0,
  ApplReturnedByIAToApplicantCount INT NOT NULL DEFAULT 0,
  PendingApplCount INT NOT NULL DEFAULT 0,
  TDRApplCount INT NOT NULL DEFAULT 0,
  BankRetApplCount INT NOT NULL DEFAULT 0,
  BankPendingApplCount INT NOT NULL DEFAULT 0,
  RectificationApplCount INT NOT NULL DEFAULT 0,
  PendForMMDisbTotalApplCount INT NOT NULL DEFAULT 0,
  ForwardedApplCount INT NOT NULL DEFAULT 0,
  SanctionedApplCount INT NOT NULL DEFAULT 0,
  MMInvolvesum_in_lakhs DECIMAL(18,2) NOT NULL DEFAULT 0,
  OnlineEdpCount INT NOT NULL DEFAULT 0,
  OfflineEdpCount INT NOT NULL DEFAULT 0,
  EdpPenApplCount INT NOT NULL DEFAULT 0,
  MMClaimedApplCount INT NOT NULL DEFAULT 0,
  MMClaimedApplsum_in_lakhs DECIMAL(18,2) NOT NULL DEFAULT 0,
  MMDisbApplCount INT NOT NULL DEFAULT 0,
  MMDisbApplsum_in_lakhs DECIMAL(18,2) NOT NULL DEFAULT 0,
  BankPendingApplsum_in_lakhs DECIMAL(18,2) NOT NULL DEFAULT 0,
  PendForMMDisbTotalApplsum_in_lakhs DECIMAL(18,2) NOT NULL DEFAULT 0,
  WithdrawApplCount INT NOT NULL DEFAULT 0,
  RejectedByBankApplCount INT NOT NULL DEFAULT 0;

 CREATE CLUSTERED INDEX IX_Metrics ON #Metrics(ZoneCode, StateName, DistrictName, AgencyCode);

 -- ---------------------------------------------------------
 -- BaseCounts (from #LoanData directly)
 -- ---------------------------------------------------------
 ;WITH BC AS (
  SELECT L.ZoneCode, L.StateName, L.DistrictName, L.AgencyCode,
   SUM(CASE WHEN (W.Seq >= 5 OR W.Seq = 0) AND (L.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(L.Datasource)), '') IS NULL) THEN 1 ELSE 0 END) AS ApplReceivedCount,
   SUM(CASE WHEN W.StageStatus = 'Return By Agency' THEN 1 ELSE 0 END) AS ApplReturnedCount,
   SUM(CASE WHEN W.StageStatus = 'Return To Applicant' THEN 1 ELSE 0 END) AS ApplReturnedByIAToApplicantCount,
   SUM(CASE WHEN W.StageStatus = 'Under Process' OR W.StageStatus = 'Application Submitted'  THEN 1 ELSE 0 END) AS PendingApplCount,
   SUM(CASE WHEN W.StageStatus = 'Return To Applicant By Bank' THEN 1 ELSE 0 END) AS BankRetApplCount
  FROM #LoanData L
  INNER JOIN #WorkFlowLevel W ON L.SchemeWrkFlowID = W.SchemeWrkFlowID
  GROUP BY L.ZoneCode, L.StateName, L.DistrictName, L.AgencyCode
 )
 UPDATE M
 SET ApplReceivedCount = BC.ApplReceivedCount,
     ApplReturnedCount = BC.ApplReturnedCount,
     PendingApplCount = BC.PendingApplCount,
     ApplReturnedByIAToApplicantCount = BC.ApplReturnedByIAToApplicantCount,
     BankRetApplCount = BC.BankRetApplCount
 FROM #Metrics M
 INNER JOIN BC ON ISNULL(M.ZoneCode,'') = ISNULL(BC.ZoneCode,'')
              AND ISNULL(M.StateName,'') = ISNULL(BC.StateName,'')
              AND ISNULL(M.DistrictName,'') = ISNULL(BC.DistrictName,'')
              AND ISNULL(M.AgencyCode,'') = ISNULL(BC.AgencyCode,'');

 -- ---------------------------------------------------------
 -- TDR Count
 -- ---------------------------------------------------------
 ;WITH SRC AS (
  SELECT MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode, Count(MainTab.TDRApplCount) AS TDRApplCount
  FROM (
   SELECT
   az.AgencyZoneCode AS ZoneCode, A.State_Name AS StateName, A.DistrictName, A.AgencyCode, B.ApplID AS TDRApplCount
   FROM t_BankProcess b WITH (NOLOCK)
   JOIN t_ApplicantData a WITH (NOLOCK) ON b.ApplID = a.ApplID
   LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = A.AgencyOffID
   WHERE a.SchemeID IN (1,2) AND b.TDRDepDate IS NOT NULL
   AND b.MMTrxfDate >= @FromDate AND b.MMTrxfDate < @ToDateExclusive
   AND (a.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@Gender IS NULL OR a.Gender = @Gender)
   AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
   AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
   AND (@LocType IS NULL OR a.UnitLocation = @LocType)
   AND (@BankIDFilter = 0 OR a.FinBankID1 = @BankIDFilter)
   AND 1 = CASE WHEN @LoanType = 'First' OR @LoanType = 'Both' THEN 1 ELSE 0 END

   UNION ALL
   SELECT
   az.AgencyZoneCode AS ZoneCode, A.State_Name AS StateName, A.DistrictName, A.AgencyCode, B.SecLoanApplID AS TDRApplCount
   FROM t_SecLoanBankProcess b WITH (NOLOCK)
   JOIN t_SecLoanApplData a ON b.SecLoanApplID = a.SecLoanApplID
   LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = A.AgencyOffID
   WHERE A.SchemeID IN (3,4) AND B.TDRDepDate IS NOT NULL
   AND b.MMTrxfDate >= @FromDate AND b.MMTrxfDate < @ToDateExclusive
   AND (a.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@Gender IS NULL OR a.Gender = @Gender)
   AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
   AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
   AND (@LocType IS NULL OR a.UnitLocation = @LocType)
   AND (@BankIDFilter = 0 OR a.FinBankID1 = @BankIDFilter)
   AND 1 = CASE WHEN @LoanType = 'Second' OR @LoanType = 'Both' THEN 1 ELSE 0 END

  ) MainTab
  GROUP BY MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode
 )
 UPDATE M SET TDRApplCount = SRC.TDRApplCount
 FROM #Metrics M
 INNER JOIN SRC ON ISNULL(M.ZoneCode,'') = ISNULL(SRC.ZoneCode,'')
              AND ISNULL(M.StateName,'') = ISNULL(SRC.StateName,'')
              AND ISNULL(M.DistrictName,'') = ISNULL(SRC.DistrictName,'')
              AND ISNULL(M.AgencyCode,'') = ISNULL(SRC.AgencyCode,'');

 -- ---------------------------------------------------------
 -- EdpCounts
 -- ---------------------------------------------------------
 ;WITH EC AS (
    SELECT MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode,
           SUM(OnlineEdpCount) AS OnlineEdpCount,
           SUM(OfflineEdpCount) AS OfflineEdpCount,
           SUM(EdpPenApplCount) AS EdpPenApplCount
    FROM (
        SELECT az.AgencyZoneCode AS ZoneCode,
               a1.State_Name AS StateName,
               a1.DistrictName,
               a1.AgencyCode,
               SUM(CASE WHEN ISNULL(e.TrainingMode, 0) = 1 AND e.EdpCentreID = 9246 AND e.CertIssueDate >= @FromDate AND e.CertIssueDate < @ToDateExclusive THEN 1 ELSE 0 END) AS OnlineEdpCount,
               SUM(CASE WHEN ISNULL(e.TrainingMode, 0) = 0 AND e.EdpCentreID <> 9246 AND e.CertIssueDate >= @FromDate AND e.CertIssueDate < @ToDateExclusive THEN 1 ELSE 0 END) AS OfflineEdpCount,
               SUM(CASE WHEN e.CertIssueDate IS NULL AND B.SancDate >= @FromDate AND B.SancDate < @ToDateExclusive THEN 1 ELSE 0 END) AS EdpPenApplCount
        FROM t_ApplicantData a1 WITH (NOLOCK)
        INNER JOIN t_BankProcess B WITH (NOLOCK) ON a1.ApplID = B.ApplID
        INNER JOIN t_EdpTraining e WITH (NOLOCK) ON a1.ApplID = e.ApplID
        INNER JOIN m_SchemeWrkFlow s ON B.WrkFlowID = S.SchemeWrkFlowID
        LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a1.AgencyOffID
        WHERE A1.SchemeID IN (1, 2)
          AND (A1.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a1.Datasource)), '') IS NULL)
          AND (@StateID IS NULL OR a1.StateID = @StateID)
          AND (@DistID IS NULL OR a1.DistID = @DistID)
          AND (@Gender IS NULL OR a1.Gender = @Gender)
          AND (@SocialCatID IS NULL OR a1.SocialCatID = @SocialCatID)
          AND (@SchemeID IS NULL OR a1.SchemeID = @SchemeID)
          AND (@AgencyCode IS NULL OR a1.AgencyCode = @AgencyCode)
          AND (@LocType IS NULL OR a1.UnitLocation = @LocType)
          AND (@BankIDFilter = 0 OR a1.FinBankID1 = @BankIDFilter)
          AND S.Seq >= 9
          AND 1 = CASE WHEN @LoanType = 'First' OR @LoanType = 'Both' THEN 1 ELSE 0 END
        GROUP BY az.AgencyZoneCode, a1.State_Name, a1.DistrictName, a1.AgencyCode
    ) MainTab
    GROUP BY MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode
)
UPDATE M
SET OnlineEdpCount = EC.OnlineEdpCount,
    OfflineEdpCount = EC.OfflineEdpCount,
    EdpPenApplCount = EC.EdpPenApplCount
FROM #Metrics M
INNER JOIN EC ON ISNULL(M.ZoneCode,'') = ISNULL(EC.ZoneCode,'')
             AND ISNULL(M.StateName,'') = ISNULL(EC.StateName,'')
             AND ISNULL(M.DistrictName,'') = ISNULL(EC.DistrictName,'')
             AND ISNULL(M.AgencyCode,'') = ISNULL(EC.AgencyCode,'');

 -- ---------------------------------------------------------
 -- ForwardedAppl
 -- ---------------------------------------------------------
 ;WITH FA AS (
  SELECT TAB.ZoneCode, TAB.StateName, TAB.DistrictName, TAB.AgencyCode, COUNT(TAB.ApplID) ForwardedApplCount
  FROM (
   SELECT az.AgencyZoneCode AS ZoneCode, A.State_Name AS StateName, A.DistrictName, A.AgencyCode, A.ApplID
   FROM t_ApplicantData A WITH (NOLOCK)
   INNER JOIN #WorkFlowLevel W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
   LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = A.AgencyOffID
   WHERE ( A.BankForwardDate >= @FromDate AND A.BankForwardDate < @ToDateExclusive ) AND
   W.seq >=6 AND W.StageStatus NOT in ('Return To Applicant','Under Process','Return By Agency','Application Withdrawn at Bank ','Application Withdrawn at Implementing Agency')
   AND (A.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR A.StateID = @StateID)
   AND (@DistID IS NULL OR A.DistID = @DistID)
   AND (@Gender IS NULL OR A.Gender = @Gender)
   AND (@SocialCatID IS NULL OR A.SocialCatID = @SocialCatID)
   AND (@SchemeID IS NULL OR A.SchemeID = @SchemeID)
   AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode)
   AND (@LocType IS NULL OR A.UnitLocation = @LocType)
   AND (@BankIDFilter = 0 OR A.FinBankID1 = @BankIDFilter)
   UNION ALL
   SELECT az.AgencyZoneCode, A1.State_Name, A1.DistrictName, A.AgencyCode, A.SecLoanApplID ApplID
   FROM t_SecLoanApplData A WITH (NOLOCK)
   INNER JOIN t_ApplicantData A1 WITH (NOLOCK) ON A.ApplID = A1.ApplID
   INNER JOIN #WorkFlowLevel W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
   LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = A1.AgencyOffID
   WHERE ( A.BankForwardDate >= @FromDate AND A.BankForwardDate < @ToDateExclusive ) AND
    W.seq >=6 AND W.StageStatus NOT in ('Return To Applicant','Under Process','Return By Agency','Application Withdrawn at Bank ','Application Withdrawn at Implementing Agency')
    AND (A1.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A1.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR A.StateID = @StateID)
   AND (@DistID IS NULL OR A.DistID = @DistID)
   AND (@Gender IS NULL OR A1.Gender = @Gender)
   AND (@SocialCatID IS NULL OR A.SocialCatID = @SocialCatID)
   AND (@SchemeID IS NULL OR A.SchemeID = @SchemeID)
   AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode)
   AND (@LocType IS NULL OR A.UnitLocation = @LocType)
   AND (@BankIDFilter = 0 OR A1.FinBankID1 = @BankIDFilter)
  ) TAB
  GROUP BY TAB.ZoneCode, TAB.StateName, TAB.DistrictName, TAB.AgencyCode
 )
 UPDATE M SET ForwardedApplCount = FA.ForwardedApplCount
 FROM #Metrics M
 INNER JOIN FA ON ISNULL(M.ZoneCode,'') = ISNULL(FA.ZoneCode,'')
              AND ISNULL(M.StateName,'') = ISNULL(FA.StateName,'')
              AND ISNULL(M.DistrictName,'') = ISNULL(FA.DistrictName,'')
              AND ISNULL(M.AgencyCode,'') = ISNULL(FA.AgencyCode,'');

 -- ---------------------------------------------------------
 -- SanctionedApplCount + MMInvolved
 -- ---------------------------------------------------------
 ;WITH SRC AS (
  SELECT az.AgencyZoneCode AS ZoneCode, a.State_Name AS StateName, a.DistrictName, a.AgencyCode,
      1 AS ApplUnit,
      CASE WHEN s.SchemeName = 'Manufacturing'
     THEN (CASE WHEN b.TotalProjectCost >= 5000000 THEN 5000000 ELSE b.TotalProjectCost END) * sd.Subsidy / 100.0
     WHEN s.SchemeName = 'Service'
     THEN (CASE WHEN b.TotalProjectCost >= 2000000 THEN 2000000 ELSE b.TotalProjectCost END) * sd.Subsidy / 100.0
     ELSE 0 END AS MMInvolveAmt
  FROM t_BankProcess b
  JOIN t_ApplicantData a ON b.ApplID = a.ApplID
  JOIN m_Scheme s ON a.SchemeID = s.SchemeID
  JOIN m_SchemeDet sd ON sd.SchemeID = s.SchemeID AND sd.SchemeDetID = a.SchemeDetID
  LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a.AgencyOffID
  WHERE s.SchemeID IN (1,2) AND b.SancDate >= @FromDate AND b.SancDate < @ToDateExclusive AND
  (a.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
  AND (@StateID IS NULL OR a.StateID = @StateID)
  AND (@DistID IS NULL OR a.DistID = @DistID)
  AND (@Gender IS NULL OR a.Gender = @Gender)
  AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
  AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
  AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
  AND (@LocType IS NULL OR a.UnitLocation = @LocType)
  AND (@BankIDFilter = 0 OR a.FinBankID1 = @BankIDFilter)
  UNION ALL
  SELECT az.AgencyZoneCode, a.State_Name, a.DistrictName, a.AgencyCode, 1,
      CASE WHEN s.SchemeName = 'Manufacturing'
     THEN (CASE WHEN b.TotalProjectCost >= 5000000 THEN 5000000 ELSE b.TotalProjectCost END) * sd.Subsidy / 100.0
     WHEN s.SchemeName = 'Service'
     THEN (CASE WHEN b.TotalProjectCost >= 2000000 THEN 2000000 ELSE b.TotalProjectCost END) * sd.Subsidy / 100.0
     ELSE 0 END
  FROM t_SecLoanBankProcess b
  JOIN t_ApplicantData a ON b.SecLoanApplID = a.ApplID
  JOIN m_Scheme s ON a.SchemeID = s.SchemeID
  JOIN m_SchemeDet sd ON sd.SchemeID = s.SchemeID AND sd.SchemeDetID = a.SchemeDetID
  LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a.AgencyOffID
  WHERE s.SchemeID IN (3,4) AND b.SancDate >= @FromDate AND b.SancDate < @ToDateExclusive
  AND (@StateID IS NULL OR a.StateID = @StateID)
  AND (@DistID IS NULL OR a.DistID = @DistID)
  AND (@Gender IS NULL OR a.Gender = @Gender)
  AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
  AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
  AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
  AND (@LocType IS NULL OR a.UnitLocation = @LocType)
  AND (@BankIDFilter = 0 OR a.FinBankID1 = @BankIDFilter)
 ),
 AGG AS (
  SELECT ZoneCode, StateName, DistrictName, AgencyCode,
         SUM(ApplUnit) AS SanctionedApplCount,
         CAST(ROUND(SUM(MMInvolveAmt)/100000.0,2) AS DECIMAL(18,2)) AS MMInvolvesum_in_lakhs
  FROM SRC
  GROUP BY ZoneCode, StateName, DistrictName, AgencyCode
 )
 UPDATE M SET SanctionedApplCount = AGG.SanctionedApplCount, MMInvolvesum_in_lakhs = AGG.MMInvolvesum_in_lakhs
 FROM #Metrics M
 INNER JOIN AGG ON ISNULL(M.ZoneCode,'') = ISNULL(AGG.ZoneCode,'')
               AND ISNULL(M.StateName,'') = ISNULL(AGG.StateName,'')
               AND ISNULL(M.DistrictName,'') = ISNULL(AGG.DistrictName,'')
               AND ISNULL(M.AgencyCode,'') = ISNULL(AGG.AgencyCode,'');

 -- ---------------------------------------------------------
 -- MMClaimedApplCount + MMClaimedApplSum
 -- ---------------------------------------------------------
 ;WITH SRC AS (
  SELECT az.AgencyZoneCode AS ZoneCode, a.State_Name AS StateName, a.DistrictName, a.AgencyCode,
         1 AS ApplUnit, ISNULL(b.MMClaimed,0) AS MMClaimedAmt
  FROM t_BankProcess b WITH (NOLOCK)
  JOIN t_ApplicantData a WITH (NOLOCK) ON b.ApplID = a.ApplID
  LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a.AgencyOffID
  WHERE b.MMClaimDt >= @FromDate AND b.MMClaimDt < @ToDateExclusive
  AND a.SchemeID IN (1,2) AND (a.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
  AND (@StateID IS NULL OR a.StateID = @StateID)
  AND (@DistID IS NULL OR a.DistID = @DistID)
  AND (@Gender IS NULL OR a.Gender = @Gender)
  AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
  AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
  AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
  AND (@LocType IS NULL OR a.UnitLocation = @LocType)
  AND (@BankIDFilter = 0 OR a.FinBankID1 = @BankIDFilter)
  AND b.MMClaimDt IS NOT NULL
  UNION ALL
  SELECT az.AgencyZoneCode, a1.State_Name, a1.DistrictName, a.AgencyCode,
  1, ISNULL(b.MMClaimed,0)
  FROM t_SecLoanBankProcess b
  JOIN t_SecLoanApplData a ON b.SecLoanApplID = a.SecLoanApplID
  JOIN t_ApplicantData a1 ON a.ApplID = a1.ApplID
  LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a1.AgencyOffID
  WHERE b.MMClaimDt >= @FromDate AND b.MMClaimDt < @ToDateExclusive
  AND a.SchemeID IN (3,4) AND (a.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
  AND (@StateID IS NULL OR a.StateID = @StateID)
  AND (@DistID IS NULL OR a.DistID = @DistID)
  AND (@Gender IS NULL OR a1.Gender = @Gender)
  AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
  AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
  AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
  AND (@LocType IS NULL OR a.UnitLocation = @LocType)
  AND (@BankIDFilter = 0 OR a1.FinBankID1 = @BankIDFilter)
  AND b.MMClaimDt IS NOT NULL
 ),
 AGG AS (
  SELECT ZoneCode, StateName, DistrictName, AgencyCode,
         SUM(ApplUnit) AS MMClaimedApplCount,
         CAST(ROUND(SUM(MMClaimedAmt)/100000.0,2) AS DECIMAL(18,2)) AS MMClaimedApplsum_in_lakhs
  FROM SRC
  GROUP BY ZoneCode, StateName, DistrictName, AgencyCode
 )
 UPDATE M SET MMClaimedApplCount = AGG.MMClaimedApplCount, MMClaimedApplsum_in_lakhs = AGG.MMClaimedApplsum_in_lakhs
 FROM #Metrics M
 INNER JOIN AGG ON ISNULL(M.ZoneCode,'') = ISNULL(AGG.ZoneCode,'')
               AND ISNULL(M.StateName,'') = ISNULL(AGG.StateName,'')
               AND ISNULL(M.DistrictName,'') = ISNULL(AGG.DistrictName,'')
               AND ISNULL(M.AgencyCode,'') = ISNULL(AGG.AgencyCode,'');

 -- ---------------------------------------------------------
 -- MMDisbApplCount + MMDisbApplSum
 -- ---------------------------------------------------------
 ;WITH SRC AS (
  SELECT az.AgencyZoneCode AS ZoneCode, a.State_Name AS StateName, a.DistrictName, a.AgencyCode,
         1 AS ApplUnit, ISNULL(b.MMAmount,0) AS MMAmt
  FROM t_BankProcess b WITH (NOLOCK)
  INNER JOIN t_ApplicantData a WITH (NOLOCK) ON b.ApplID = a.ApplID
  INNER JOIN t_NodalBankMMClaimed M WITH (NOLOCK) ON b.ApplID = M.ApplID
  LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a.AgencyOffID
  WHERE b.MMTrxfDate >= @FromDate AND b.MMTrxfDate < @ToDateExclusive AND b.MMTrxfDate IS NOT NULL
  AND M.IsMMProcessed = 1 AND a.SchemeID IN (1,2)
  AND (@StateID IS NULL OR a.StateID = @StateID)
  AND (@DistID IS NULL OR a.DistID = @DistID)
  AND (@Gender IS NULL OR a.Gender = @Gender)
  AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
  AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
  AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
  AND (@LocType IS NULL OR a.UnitLocation = @LocType)
  AND (@BankIDFilter = 0 OR a.FinBankID1 = @BankIDFilter)
  UNION ALL
  SELECT az.AgencyZoneCode, a1.State_Name, a1.DistrictName, a.AgencyCode, 1, ISNULL(b.MMAmount,0)
  FROM t_SecLoanBankProcess b
  JOIN t_SecLoanApplData a ON b.SecLoanApplID = a.SecLoanApplID
  JOIN t_ApplicantData a1 ON a.ApplID = a1.ApplID
  INNER JOIN t_SecNodalBankMMClaimed M WITH (NOLOCK) ON b.SecLoanApplID = M.SecLoanApplID
  LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a1.AgencyOffID
  WHERE b.MMTrxfDate >= @FromDate AND b.MMTrxfDate < @ToDateExclusive AND b.MMTrxfDate IS NOT NULL
  AND M.IsMMProcessed = 1
  AND (@StateID IS NULL OR a.StateID = @StateID)
  AND (@DistID IS NULL OR a.DistID = @DistID)
  AND (@Gender IS NULL OR a1.Gender = @Gender)
  AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
  AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
  AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
  AND (@LocType IS NULL OR a.UnitLocation = @LocType)
  AND (@BankIDFilter = 0 OR a1.FinBankID1 = @BankIDFilter)
 ),
 AGG AS (
  SELECT ZoneCode, StateName, DistrictName, AgencyCode,
         SUM(ApplUnit) AS MMDisbApplCount,
         CAST(ROUND(SUM(MMAmt)/100000.0,2) AS DECIMAL(18,2)) AS MMDisbApplsum_in_lakhs
  FROM SRC
  GROUP BY ZoneCode, StateName, DistrictName, AgencyCode
 )
 UPDATE M SET MMDisbApplCount = AGG.MMDisbApplCount, MMDisbApplsum_in_lakhs = AGG.MMDisbApplsum_in_lakhs
 FROM #Metrics M
 INNER JOIN AGG ON ISNULL(M.ZoneCode,'') = ISNULL(AGG.ZoneCode,'')
               AND ISNULL(M.StateName,'') = ISNULL(AGG.StateName,'')
               AND ISNULL(M.DistrictName,'') = ISNULL(AGG.DistrictName,'')
               AND ISNULL(M.AgencyCode,'') = ISNULL(AGG.AgencyCode,'');

 -- ---------------------------------------------------------
 -- BankPendingApplCount + Sum
 -- ---------------------------------------------------------
 ;WITH SRC AS (
    SELECT MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode,
     SUM(MainTab.ApplCountVal) AS BankPendingApplCount,
     SUM(MainTab.Amt) AS BankPendingApplsum_in_lakhs
 FROM (
        SELECT az.AgencyZoneCode AS ZoneCode, a.State_Name AS StateName, a.DistrictName, a.AgencyCode, Count(a.ApplID) AS ApplCountVal,
              CAST(ROUND(SUM(CASE WHEN s.SchemeName = 'Manufacturing'
     THEN (CASE WHEN a.TotalProjectCost >= 5000000 THEN 5000000 ELSE a.TotalProjectCost END) * sd.Subsidy / 100.0
     WHEN s.SchemeName = 'Service' THEN (CASE WHEN a.TotalProjectCost >= 2000000 THEN 2000000 ELSE a.TotalProjectCost END) * sd.Subsidy / 100.0
     ELSE 0 END )/100000.0,2) AS decimal(18,2)) AS Amt
        FROM t_ApplicantData a WITH (NOLOCK)
        JOIN m_SchemeWrkFlow s1 WITH (NOLOCK) ON a.SchemeWrkFlowID = s1.SchemeWrkFlowID
        JOIN m_Scheme s WITH (NOLOCK) ON a.SchemeID = s.SchemeID
        JOIN m_SchemeDet sd WITH (NOLOCK) ON sd.SchemeID = s.SchemeID AND sd.SchemeDetID = a.SchemeDetID
        LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a.AgencyOffID
        WHERE s1.seq >= 6
          AND s1.StageStatus NOT IN ('Return To Applicant', 'Under Process', 'Return By Agency')
          AND s1.StageStatus in ('Further Process','Forwarded To Primary Bank')
          AND (a.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
          AND a.BankForwardDate >= @FromDate AND a.BankForwardDate < @ToDateExclusive
          AND a.ApplCode NOT IN (
              Select A.ApplCode from t_ApplicantData A WITH (NOLOCK)
              INNER JOIN m_SchemeWrkFlow W WITH (NOLOCK) ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
              where W.StageStatus in ('Return To Applicant By Bank')
              AND (A.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
                AND (@StateID IS NULL OR A.StateID = @StateID)
                AND (@DistID IS NULL OR A.DistID = @DistID)
                AND (@Gender IS NULL OR A.Gender = @Gender)
                AND (@SocialCatID IS NULL OR A.SocialCatID = @SocialCatID)
                AND (@SchemeID IS NULL OR A.SchemeID = @SchemeID)
                AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode)
                AND (@LocType IS NULL OR A.UnitLocation = @LocType)
                AND (@BankIDFilter = 0 OR A.FinBankID1 = @BankIDFilter)
              Union
              Select A.ApplCode from t_ApplicantData A WITH (NOLOCK)
              INNER JOIN m_SchemeWrkFlow W WITH (NOLOCK) ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
              Inner join t_BankProcess B WITH (NOLOCK) ON A.ApplID = b.ApplID
              where b.SancDate >= @FromDate AND b.SancDate < @ToDateExclusive AND
                (A.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
                AND (@StateID IS NULL OR A.StateID = @StateID)
                AND (@DistID IS NULL OR A.DistID = @DistID)
                AND (@Gender IS NULL OR A.Gender = @Gender)
                AND (@SocialCatID IS NULL OR A.SocialCatID = @SocialCatID)
                AND (@SchemeID IS NULL OR A.SchemeID = @SchemeID)
                AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode)
                AND (@LocType IS NULL OR A.UnitLocation = @LocType)
                AND (@BankIDFilter = 0 OR A.FinBankID1 = @BankIDFilter)
          )
          AND (@StateID IS NULL OR a.StateID = @StateID)
          AND (@DistID IS NULL OR a.DistID = @DistID)
          AND (@Gender IS NULL OR a.Gender = @Gender)
          AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
          AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
          AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
          AND (@LocType IS NULL OR a.UnitLocation = @LocType)
          AND (@BankIDFilter = 0 OR a.FinBankID1 = @BankIDFilter)
        AND 1 = CASE WHEN @LoanType = 'First' OR @LoanType = 'Both' THEN 1 ELSE 0 END
        GROUP BY az.AgencyZoneCode, a.State_Name, a.DistrictName, a.AgencyCode

        UNION ALL

        SELECT az.AgencyZoneCode, a.State_Name, a.DistrictName, a.AgencyCode,
               Count(a.SecLoanApplID) AS ApplCountVal,
      CAST(ROUND(SUM(CASE WHEN s.SchemeName = 'Manufacturing'
     THEN (CASE WHEN a.TotalProjectCost >= 5000000 THEN 5000000 ELSE a.TotalProjectCost END) * sd.Subsidy / 100.0
     WHEN s.SchemeName = 'Service' THEN (CASE WHEN a.TotalProjectCost >= 2000000 THEN 2000000 ELSE a.TotalProjectCost END) * sd.Subsidy / 100.0
     ELSE 0 END ) / 100000.0,2) AS decimal(18,2)) AS Amt
        FROM t_SecLoanApplData a WITH (NOLOCK)
        JOIN m_SchemeWrkFlow s1 ON a.SchemeWrkFlowID = S1.SchemeWrkFlowID
        JOIN m_Scheme s ON a.SchemeID = s.SchemeID
        JOIN m_SchemeDet sd ON sd.SchemeID = s.SchemeID AND sd.SchemeDetID = a.SchemeDetID
        LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a.AgencyOffID
        WHERE s1.seq >= 6
          AND s1.StageStatus NOT IN ('Return To Applicant', 'Under Process', 'Return By Agency')
          AND (a.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
          AND a.BankForwardDate >= @FromDate AND a.BankForwardDate < @ToDateExclusive
          AND a.SecApplCode NOT IN (
              Select A.SecApplCode from t_SecLoanApplData A
              INNER JOIN m_SchemeWrkFlow W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
              where W.StageStatus in ('Return To Applicant By Bank') AND
              (A.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
                AND (@StateID IS NULL OR A.StateID = @StateID)
                AND (@DistID IS NULL OR A.DistID = @DistID)
                AND (@Gender IS NULL OR A.Gender = @Gender)
                AND (@SocialCatID IS NULL OR A.SocialCatID = @SocialCatID)
                AND (@SchemeID IS NULL OR A.SchemeID = @SchemeID)
                AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode)
                AND (@LocType IS NULL OR A.UnitLocation = @LocType)
                AND (@BankIDFilter = 0 OR A.FinBankID1 = @BankIDFilter)
              Union
              Select A.SecApplCode from t_SecLoanApplData A
              INNER JOIN m_SchemeWrkFlow W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
              Inner join t_SecLoanBankProcess B ON A.SecLoanApplID = b.SecLoanApplID
              where b.SancDate >= @FromDate AND b.SancDate < @ToDateExclusive AND
             (A.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
                AND (@StateID IS NULL OR A.StateID = @StateID)
                AND (@DistID IS NULL OR A.DistID = @DistID)
                AND (@Gender IS NULL OR A.Gender = @Gender)
                AND (@SocialCatID IS NULL OR A.SocialCatID = @SocialCatID)
                AND (@SchemeID IS NULL OR A.SchemeID = @SchemeID)
                AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode)
                AND (@LocType IS NULL OR A.UnitLocation = @LocType)
                AND (@BankIDFilter = 0 OR A.FinBankID1 = @BankIDFilter)
          )
          AND (@StateID IS NULL OR a.StateID = @StateID)
          AND (@DistID IS NULL OR a.DistID = @DistID)
          AND (@Gender IS NULL OR a.Gender = @Gender)
          AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
          AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
          AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
          AND (@LocType IS NULL OR a.UnitLocation = @LocType)
          AND (@BankIDFilter = 0 OR a.FinBankID1 = @BankIDFilter)
          AND 1 = CASE WHEN @LoanType = 'Second' OR @LoanType = 'Both' THEN 1 ELSE 0 END
        GROUP BY az.AgencyZoneCode, a.State_Name, a.DistrictName, a.AgencyCode
    ) MainTab
    GROUP BY MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode
),
AGG AS (
    SELECT ZoneCode, StateName, DistrictName, AgencyCode,
           SUM(BankPendingApplCount) AS BankPendingApplCount,
     CAST(ROUND(ISNULL(SUM(BankPendingApplsum_in_lakhs), 0), 2) AS DECIMAL(18,2)) AS BankPendingApplsum_in_lakhs
    FROM SRC
    GROUP BY ZoneCode, StateName, DistrictName, AgencyCode
)
UPDATE M
SET BankPendingApplCount = AGG.BankPendingApplCount,
    BankPendingApplsum_in_lakhs = AGG.BankPendingApplsum_in_lakhs
FROM #Metrics M
INNER JOIN AGG ON ISNULL(M.ZoneCode,'') = ISNULL(AGG.ZoneCode,'')
              AND ISNULL(M.StateName,'') = ISNULL(AGG.StateName,'')
              AND ISNULL(M.DistrictName,'') = ISNULL(AGG.DistrictName,'')
              AND ISNULL(M.AgencyCode,'') = ISNULL(AGG.AgencyCode,'');

 -- ---------------------------------------------------------
 -- PendForMMDisbTotalAppl (MMTrxfDate IS NULL)
 -- ---------------------------------------------------------
 ;WITH SRC AS (
    SELECT MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode,
     SUM(MainTab.ApplCountVal) AS PendForMMDisbTotalApplCount,
     SUM(MainTab.Amt) AS PendForMMDisbTotalApplsum_in_lakhs
 FROM (
        SELECT az.AgencyZoneCode AS ZoneCode,
               a.State_Name AS StateName,
               a.DistrictName,
               a.AgencyCode,
      CAST(ROUND(SUM(b.MMClaimed) / 100000.0, 2) AS DECIMAL(18,2)) AS Amt,
      COUNT(b.ApplID) AS ApplCountVal
        FROM t_BankProcess b
        JOIN t_ApplicantData a ON b.ApplID = a.ApplID
        JOIN m_Scheme s ON a.SchemeID = s.SchemeID
        JOIN m_SchemeDet sd ON sd.SchemeID = s.SchemeID AND sd.SchemeDetID = a.SchemeDetID
        LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a.AgencyOffID
        WHERE s.SchemeID IN (1, 2)
          AND b.MMClaimDt >= @FromDate
          AND b.MMClaimDt < @ToDateExclusive
          AND (a.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
          AND b.MMTrxfDate IS NULL
          AND (@StateID IS NULL OR a.StateID = @StateID)
          AND (@DistID IS NULL OR a.DistID = @DistID)
          AND (@Gender IS NULL OR a.Gender = @Gender)
          AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
          AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
          AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
          AND (@LocType IS NULL OR a.UnitLocation = @LocType)
          AND (@BankIDFilter = 0 OR a.FinBankID1 = @BankIDFilter)
        GROUP BY az.AgencyZoneCode, a.State_Name, a.DistrictName, a.AgencyCode

        UNION ALL

        SELECT az.AgencyZoneCode,
               a.State_Name,
               a.DistrictName,
               a.AgencyCode,
      CAST(ROUND(SUM(b.MMClaimed) / 100000.0, 2) AS DECIMAL(18,2)) AS Amt,
      COUNT(b.SecLoanApplID) AS ApplCountVal
        FROM t_SecLoanBankProcess b
        JOIN t_ApplicantData a ON b.SecLoanApplID = a.ApplID
        JOIN m_Scheme s ON a.SchemeID = s.SchemeID
        JOIN m_SchemeDet sd ON sd.SchemeID = s.SchemeID AND sd.SchemeDetID = a.SchemeDetID
        LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a.AgencyOffID
        WHERE s.SchemeID IN (3, 4)
          AND b.MMClaimDt >= @FromDate
          AND b.MMClaimDt < @ToDateExclusive
          AND b.MMTrxfDate IS NULL
          AND (@StateID IS NULL OR a.StateID = @StateID)
          AND (@DistID IS NULL OR a.DistID = @DistID)
          AND (@Gender IS NULL OR a.Gender = @Gender)
          AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
         AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
          AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
          AND (@LocType IS NULL OR a.UnitLocation = @LocType)
          AND (@BankIDFilter = 0 OR a.FinBankID1 = @BankIDFilter)
        GROUP BY az.AgencyZoneCode, a.State_Name, a.DistrictName, a.AgencyCode
    ) MainTab
    GROUP BY MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode
),
AGG AS (
    SELECT ZoneCode, StateName, DistrictName, AgencyCode,
           SUM(PendForMMDisbTotalApplCount) AS PendForMMDisbTotalApplCount,
           CAST(ROUND(SUM(PendForMMDisbTotalApplsum_in_lakhs), 2) AS DECIMAL(18,2)) AS PendForMMDisbTotalApplsum_in_lakhs
    FROM SRC
    GROUP BY ZoneCode, StateName, DistrictName, AgencyCode
)
UPDATE M
SET PendForMMDisbTotalApplCount = AGG.PendForMMDisbTotalApplCount,
    PendForMMDisbTotalApplsum_in_lakhs = AGG.PendForMMDisbTotalApplsum_in_lakhs
FROM #Metrics M
INNER JOIN AGG ON ISNULL(M.ZoneCode,'') = ISNULL(AGG.ZoneCode,'')
              AND ISNULL(M.StateName,'') = ISNULL(AGG.StateName,'')
              AND ISNULL(M.DistrictName,'') = ISNULL(AGG.DistrictName,'')
              AND ISNULL(M.AgencyCode,'') = ISNULL(AGG.AgencyCode,'');

 -- ---------------------------------------------------------
 -- RefferedImplementingOrBankByValidation COUNT (Rectification)
 -- ---------------------------------------------------------
 ;WITH SRC AS (
    SELECT MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode,
     COUNT(MainTab.RectificationApplCount) AS RectificationApplCount
 FROM (
        SELECT az.AgencyZoneCode AS ZoneCode,
               a.State_Name AS StateName,
               a.DistrictName,
               a.AgencyCode,
         A.ApplID AS RectificationApplCount
        FROM t_ApplicantData A WITH (NOLOCK)
  INNER JOIN t_BankProcess b WITH (NOLOCK) ON A.ApplID = B.ApplID
  LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a.AgencyOffID
  WHERE A.isRefferedImplementingOrBankByValidation = 1
    AND b.MMClaimDt >= @FromDate
          AND b.MMClaimDt < @ToDateExclusive
    AND b.MMClaimDt IS NOT NULL
          AND b.isUnderValidation = 0
          AND b.isUnderVarification = 0
    AND (A.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
    AND (@StateID IS NULL OR a.StateID = @StateID)
    AND (@DistID IS NULL OR a.DistID = @DistID)
    AND (@Gender IS NULL OR a.Gender = @Gender)
    AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
    AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
    AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
    AND (@LocType IS NULL OR a.UnitLocation = @LocType)
    AND (@BankIDFilter = 0 OR a.FinBankID1 = @BankIDFilter)
    AND 1 = CASE WHEN @LoanType = 'First' OR @LoanType = 'Both' THEN 1 ELSE 0 END

  UNION ALL

        SELECT az.AgencyZoneCode AS ZoneCode,
               a.State_Name AS StateName,
               a.DistrictName,
               a.AgencyCode,
         A.SecLoanApplID AS RectificationApplCount
        FROM t_SecLoanApplData A WITH (NOLOCK)
  INNER JOIN t_SecLoanBankProcess b WITH (NOLOCK) ON A.SecLoanApplID = b.SecLoanApplID
  LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = a.AgencyOffID
  WHERE A.isRetBackByVali = 1
    AND b.MMClaimDt >= @FromDate
          AND b.MMClaimDt < @ToDateExclusive
    AND b.MMClaimDt IS NOT NULL
          AND b.isUnderValidation = 0
          AND b.isUnderVarification = 0
    AND (A.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
    AND (@StateID IS NULL OR a.StateID = @StateID)
    AND (@DistID IS NULL OR a.DistID = @DistID)
    AND (@Gender IS NULL OR a.Gender = @Gender)
    AND (@SocialCatID IS NULL OR a.SocialCatID = @SocialCatID)
    AND (@SchemeID IS NULL OR a.SchemeID = @SchemeID)
    AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
    AND (@LocType IS NULL OR a.UnitLocation = @LocType)
    AND (@BankIDFilter = 0 OR a.FinBankID1 = @BankIDFilter)
    AND 1 = CASE WHEN @LoanType = 'Second' OR @LoanType = 'Both' THEN 1 ELSE 0 END
    ) MainTab
    GROUP BY MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode
)
UPDATE M
SET RectificationApplCount = SRC.RectificationApplCount
FROM #Metrics M
INNER JOIN SRC ON ISNULL(M.ZoneCode,'') = ISNULL(SRC.ZoneCode,'')
              AND ISNULL(M.StateName,'') = ISNULL(SRC.StateName,'')
              AND ISNULL(M.DistrictName,'') = ISNULL(SRC.DistrictName,'')
              AND ISNULL(M.AgencyCode,'') = ISNULL(SRC.AgencyCode,'');

 -- ---------------------------------------------------------
 -- WithdrawApplications
 -- ---------------------------------------------------------
 ;WITH SRC AS (
  SELECT MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode, COUNT(MainTab.ApplID) WithdrawApplCount
  FROM (
   SELECT az.AgencyZoneCode AS ZoneCode,  a.State_Name AS StateName,   a.DistrictName,  a.AgencyCode,
     A.ApplID ApplID
   FROM t_ApplicantData A WITH (NOLOCK)
   INNER JOIN #WorkFlowLevel W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
   LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = A.AgencyOffID
   WHERE ( A.WithdrawDate >= @FromDate AND A.WithdrawDate < @ToDateExclusive )
   AND W.StageStatus in ('Application Withdrawn at Bank ','Application Withdrawn at Implementing Agency') AND
    (A.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR A.StateID = @StateID)
   AND (@DistID IS NULL OR A.DistID = @DistID)
   AND (@Gender IS NULL OR A.Gender = @Gender)
   AND (@SocialCatID IS NULL OR A.SocialCatID = @SocialCatID)
   AND (@SchemeID IS NULL OR A.SchemeID = @SchemeID)
   AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode)
   AND (@LocType IS NULL OR A.UnitLocation = @LocType)
   AND (@BankIDFilter = 0 OR A.FinBankID1 = @BankIDFilter)
   AND 1 = CASE WHEN @LoanType = 'First' OR @LoanType = 'Both' THEN 1 ELSE 0 END
   UNION
   SELECT az.AgencyZoneCode AS ZoneCode,  a.State_Name AS StateName,   a.DistrictName,  a.AgencyCode,
     A.SecLoanApplID ApplID
   FROM t_SecLoanApplData A WITH (NOLOCK)
   INNER JOIN #WorkFlowLevel W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
   LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = A.AgencyOffID
   WHERE ( A.ModifyOn >= @FromDate AND A.ModifyOn < @ToDateExclusive ) AND
   W.StageStatus in ('Application Withdrawn at Bank ','Application Withdrawn at Implementing Agency')
   AND (A.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR A.StateID = @StateID)
   AND (@DistID IS NULL OR A.DistID = @DistID)
   AND (@Gender IS NULL OR A.Gender = @Gender)
   AND (@SocialCatID IS NULL OR A.SocialCatID = @SocialCatID)
   AND (@SchemeID IS NULL OR A.SchemeID = @SchemeID)
   AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode)
   AND (@LocType IS NULL OR A.UnitLocation = @LocType)
   AND (@BankIDFilter = 0 OR A.FinBankID1 = @BankIDFilter)
   AND 1 = CASE WHEN @LoanType = 'Second' OR @LoanType = 'Both' THEN 1 ELSE 0 END
   ) MainTab
  GROUP BY MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode
 )
 UPDATE M
SET WithdrawApplCount = SRC.WithdrawApplCount
FROM #Metrics M
INNER JOIN SRC ON ISNULL(M.ZoneCode,'') = ISNULL(SRC.ZoneCode,'')
              AND ISNULL(M.StateName,'') = ISNULL(SRC.StateName,'')
              AND ISNULL(M.DistrictName,'') = ISNULL(SRC.DistrictName,'')
              AND ISNULL(M.AgencyCode,'') = ISNULL(SRC.AgencyCode,'');

 -- ---------------------------------------------------------
 -- RejectedByBankApplCount
 -- ---------------------------------------------------------
 ;WITH SRC AS (
  SELECT MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode, COUNT(MainTab.ApplID) AS RejectedByBankApplCount
  FROM (
   SELECT az.AgencyZoneCode AS ZoneCode,  a.State_Name AS StateName,   a.DistrictName,  a.AgencyCode,
     A.ApplID ApplID
   FROM t_ApplicantData A WITH (NOLOCK)
   Inner Join t_BankProcess B  WITH (NOLOCK) ON A.ApplID = B.ApplID
   INNER JOIN #WorkFlowLevel W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
   LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = A.AgencyOffID
   WHERE ( A.BankForwardDate >= @FromDate AND A.BankForwardDate < @ToDateExclusive )
   AND W.StageStatus in ('Rejected / Return')
   AND (A.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR A.StateID = @StateID)
   AND (@DistID IS NULL OR A.DistID = @DistID)
   AND (@Gender IS NULL OR A.Gender = @Gender)
   AND (@SocialCatID IS NULL OR A.SocialCatID = @SocialCatID)
   AND (@SchemeID IS NULL OR A.SchemeID = @SchemeID)
   AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode)
   AND (@LocType IS NULL OR A.UnitLocation = @LocType)
   AND (@BankIDFilter = 0 OR A.FinBankID1 = @BankIDFilter)
   AND 1 = CASE WHEN @LoanType = 'First' OR @LoanType = 'Both' THEN 1 ELSE 0 END
   UNION
   SELECT az.AgencyZoneCode AS ZoneCode,  a.State_Name AS StateName,   a.DistrictName,  a.AgencyCode,
     A.SecLoanApplID ApplID
   FROM t_SecLoanApplData A WITH (NOLOCK)
   INNER JOIN #WorkFlowLevel W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
   Inner Join t_BankProcess B  WITH (NOLOCK) ON A.ApplID = B.ApplID
   LEFT JOIN #AgencyZoneMap az ON az.AgencyOffID = A.AgencyOffID
   WHERE ( A.BankForwardDate >= @FromDate AND A.BankForwardDate < @ToDateExclusive )
   AND W.StageStatus in ('Rejected / Return')
   AND (A.DataSource IN ('MIG','MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR A.StateID = @StateID)
   AND (@DistID IS NULL OR A.DistID = @DistID)
   AND (@Gender IS NULL OR A.Gender = @Gender)
   AND (@SocialCatID IS NULL OR A.SocialCatID = @SocialCatID)
   AND (@SchemeID IS NULL OR A.SchemeID = @SchemeID)
   AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode)
   AND (@LocType IS NULL OR A.UnitLocation = @LocType)
   AND (@BankIDFilter = 0 OR A.FinBankID1 = @BankIDFilter)
   AND 1 = CASE WHEN @LoanType = 'Second' OR @LoanType = 'Both' THEN 1 ELSE 0 END
   ) MainTab
  GROUP BY MainTab.ZoneCode, MainTab.StateName, MainTab.DistrictName, MainTab.AgencyCode
 )
  UPDATE M
SET RejectedByBankApplCount = SRC.RejectedByBankApplCount
FROM #Metrics M
INNER JOIN SRC ON ISNULL(M.ZoneCode,'') = ISNULL(SRC.ZoneCode,'')
              AND ISNULL(M.StateName,'') = ISNULL(SRC.StateName,'')
              AND ISNULL(M.DistrictName,'') = ISNULL(SRC.DistrictName,'')
              AND ISNULL(M.AgencyCode,'') = ISNULL(SRC.AgencyCode,'')

 -- =========================================================
 -- FINAL: roll #Metrics up to the two columns @Level needs.
 -- =========================================================
 IF @Level = 'ZoneWise'
 BEGIN
  SELECT
   ZoneCode AS GroupCode,
   AgencyCode AS SubCode,
   GROUPING(AgencyCode) AS IsTotalRow,
   SUM(ApplReceivedCount) AS ApplReceivedCount,
   SUM(ApplReturnedCount) AS ApplReturnedCount,
   SUM(ApplReturnedByIAToApplicantCount) AS ApplReturnedByIAToApplicantCount,
   SUM(WithdrawApplCount) AS WithdrawApplCount,
   SUM(PendingApplCount) AS PendingApplCount,
   SUM(ForwardedApplCount) AS ForwardedApplCount,
   SUM(SanctionedApplCount) AS SanctionedApplCount,
   SUM(MMInvolvesum_in_lakhs) AS MMInvolvesum_in_lakhs,
   SUM(OnlineEdpCount) AS OnlineEdpCount,
   SUM(OfflineEdpCount) AS OfflineEdpCount,
   SUM(MMClaimedApplCount) AS MMClaimedApplCount,
   SUM(MMClaimedApplsum_in_lakhs) AS MMClaimedApplsum_in_lakhs,
   SUM(MMDisbApplCount) AS MMDisbApplCount,
   SUM(MMDisbApplsum_in_lakhs) AS MMDisbApplsum_in_lakhs,
   SUM(TDRApplCount) AS TDRApplCount,
   SUM(EdpPenApplCount) AS EdpPenApplCount,
   SUM(BankRetApplCount) AS BankRetApplCount,
   SUM(BankPendingApplCount) AS BankPendingApplCount,
   SUM(BankPendingApplsum_in_lakhs) AS BankPendingApplsum_in_lakhs,
   SUM(RectificationApplCount) AS RectificationApplCount,
   SUM(PendForMMDisbTotalApplCount) AS PendForMMDisbTotalApplCount,
   SUM(PendForMMDisbTotalApplsum_in_lakhs) AS PendForMMDisbTotalApplsum_in_lakhs,
      SUM(RejectedByBankApplCount) AS RejectedByBankApplCount
  FROM #Metrics
  WHERE ZoneCode IS NOT NULL
  GROUP BY GROUPING SETS ((ZoneCode, AgencyCode), (ZoneCode))
  ORDER BY ZoneCode, IsTotalRow, AgencyCode;
 END
 ELSE IF @Level = 'StateWise'
 BEGIN
  SELECT
   ZoneCode AS GroupCode,
   StateName AS SubCode,
   GROUPING(StateName) AS IsTotalRow,
   SUM(ApplReceivedCount) AS ApplReceivedCount,
   SUM(ApplReturnedCount) AS ApplReturnedCount,
   SUM(ApplReturnedByIAToApplicantCount) AS ApplReturnedByIAToApplicantCount,
   SUM(WithdrawApplCount) AS WithdrawApplCount,
   SUM(PendingApplCount) AS PendingApplCount,
   SUM(ForwardedApplCount) AS ForwardedApplCount,
   SUM(SanctionedApplCount) AS SanctionedApplCount,
   SUM(MMInvolvesum_in_lakhs) AS MMInvolvesum_in_lakhs,
   SUM(OnlineEdpCount) AS OnlineEdpCount,
   SUM(OfflineEdpCount) AS OfflineEdpCount,
   SUM(MMClaimedApplCount) AS MMClaimedApplCount,
   SUM(MMClaimedApplsum_in_lakhs) AS MMClaimedApplsum_in_lakhs,
   SUM(MMDisbApplCount) AS MMDisbApplCount,
   SUM(MMDisbApplsum_in_lakhs) AS MMDisbApplsum_in_lakhs,
   SUM(TDRApplCount) AS TDRApplCount,
   SUM(EdpPenApplCount) AS EdpPenApplCount,
   SUM(BankRetApplCount) AS BankRetApplCount,
   SUM(BankPendingApplCount) AS BankPendingApplCount,
   SUM(BankPendingApplsum_in_lakhs) AS BankPendingApplsum_in_lakhs,
   SUM(RectificationApplCount) AS RectificationApplCount,
   SUM(PendForMMDisbTotalApplCount) AS PendForMMDisbTotalApplCount,
   SUM(PendForMMDisbTotalApplsum_in_lakhs) AS PendForMMDisbTotalApplsum_in_lakhs,
       SUM(RejectedByBankApplCount) AS RejectedByBankApplCount
  FROM #Metrics
  WHERE ZoneCode IS NOT NULL AND StateName IS NOT NULL
  AND (@ZoneCode IS NULL OR ZoneCode = @ZoneCode)
  GROUP BY GROUPING SETS ((ZoneCode, StateName), (ZoneCode))
  ORDER BY ZoneCode, IsTotalRow, StateName;
 END
 ELSE IF @Level = 'DistrictWise'
 BEGIN
  SELECT
   StateName AS GroupCode,
   DistrictName AS SubCode,
   GROUPING(DistrictName) AS IsTotalRow,
   SUM(ApplReceivedCount) AS ApplReceivedCount,
   SUM(ApplReturnedCount) AS ApplReturnedCount,
   SUM(ApplReturnedByIAToApplicantCount) AS ApplReturnedByIAToApplicantCount,
   SUM(WithdrawApplCount) AS WithdrawApplCount,
   SUM(PendingApplCount) AS PendingApplCount,
   SUM(ForwardedApplCount) AS ForwardedApplCount,
   SUM(SanctionedApplCount) AS SanctionedApplCount,
   SUM(MMInvolvesum_in_lakhs) AS MMInvolvesum_in_lakhs,
   SUM(OnlineEdpCount) AS OnlineEdpCount,
   SUM(OfflineEdpCount) AS OfflineEdpCount,
   SUM(MMClaimedApplCount) AS MMClaimedApplCount,
   SUM(MMClaimedApplsum_in_lakhs) AS MMClaimedApplsum_in_lakhs,
   SUM(MMDisbApplCount) AS MMDisbApplCount,
   SUM(MMDisbApplsum_in_lakhs) AS MMDisbApplsum_in_lakhs,
   SUM(TDRApplCount) AS TDRApplCount,
   SUM(EdpPenApplCount) AS EdpPenApplCount,
   SUM(BankRetApplCount) AS BankRetApplCount,
   SUM(BankPendingApplCount) AS BankPendingApplCount,
   SUM(BankPendingApplsum_in_lakhs) AS BankPendingApplsum_in_lakhs,
   SUM(RectificationApplCount) AS RectificationApplCount,
   SUM(PendForMMDisbTotalApplCount) AS PendForMMDisbTotalApplCount,
   SUM(PendForMMDisbTotalApplsum_in_lakhs) AS PendForMMDisbTotalApplsum_in_lakhs,
   SUM(RejectedByBankApplCount) AS RejectedByBankApplCount
  FROM #Metrics
  WHERE StateName IS NOT NULL AND DistrictName IS NOT NULL
  GROUP BY GROUPING SETS ((StateName, DistrictName), (StateName))
  ORDER BY StateName, IsTotalRow, DistrictName;
 END
END TRY
BEGIN CATCH
 SELECT ERROR_NUMBER() AS ErrorNumber, ERROR_MESSAGE() AS ErrorMessage;
END CATCH
END
