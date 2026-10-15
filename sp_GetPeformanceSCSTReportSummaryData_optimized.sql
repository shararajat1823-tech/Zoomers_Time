/* =====================================================================================
   sp_GetPeformanceSCSTReportSummaryData  -- PERFORMANCE-OPTIMIZED VERSION

   Goal: cut execution time and CPU WITHOUT changing the result set.

   What changed (and why it is safe):
   ---------------------------------------------------------------------------------
   1. SARGable date filters (BIGGEST WIN).
      Every "CAST(col AS date) >= @FromDate AND CAST(col AS date) < @ToDateExclusive"
      (and the FinalSubDate "CAST(col AS date) BETWEEN @FromDate AND @ToDate" form) was
      rewritten to the half-open range "col >= @FromDate AND col < @ToDateExclusive".
      Because @FromDate/@ToDateExclusive are DATE (midnight) and the range is half-open,
      the rows returned are IDENTICAL -- but CASTing the COLUMN makes the predicate
      non-SARGable, forcing an index/table SCAN plus a per-row datetime->date conversion
      on every row. The uncast form lets SQL Server SEEK a date index and skips the
      conversion. This is the main driver of the high CPU / long runtime.

      Note on the #PeformanceSCSTReport build: the original used
         CAST(FinalSubDate AS date) >= @FromDate AND CAST(FinalSubDate AS date) <= @ToDate
      i.e. INCLUSIVE of @ToDate. "col < @ToDateExclusive" (= @ToDate + 1 day at midnight)
      covers every time on @ToDate, so it returns the same rows.

   2. OPTION (RECOMPILE) on the two heavy statements.
      @Level selects completely different GROUP BY columns, and @StateID/@DistID/
      @AgencyCode are all optional. RECOMPILE lets the optimizer build a plan tailored to
      the actual @Level and supplied filters each run (no bad cached-plan reuse), which is
      the right trade for a reporting proc that runs occasionally over large tables.

   Deliberately NOT changed (to preserve results exactly):
      - UNION (not UNION ALL) in BankReturn / FwdToBankCurrYr: intentional de-duplication.
      - All join logic, scheme filters and metric definitions, including the existing
        "t_SecLoanApplData A JOIN t_BankProcess b ON A.ApplID = B.ApplID" join in the
        Rectification block (carried over verbatim; looks like a latent bug but changing
        it would change results -- raise with the team separately).
      - The unqualified isUnderValidation / isUnderVarification columns (they live on the
        applicant tables, which is where they resolve here).

   SUPPORTING INDEXES: the SARGable rewrite only pays off with an index to seek. The same
   index script created for sp_GetMISReportDataGrouping
   (sp_GetMISReportDataGrouping_supporting_indexes.sql) already covers the date columns
   this proc uses: t_ApplicantData(FinalSubDate, BankForwardDate), t_BankProcess(SancDate,
   MMClaimDt, MMTrxfDate), the Sec-loan equivalents, and t_EdpTraining(ApplID). Deploy it
   once (DBA task) if not already present.
   =====================================================================================

   -- EXEC [dbo].[sp_GetPeformanceSCSTReportSummaryData] 'AgencyWise',null,null,'2025-04-01','2026-03-31',''
*/

CREATE OR ALTER PROCEDURE [dbo].[sp_GetPeformanceSCSTReportSummaryData]
@Level Varchar(50),
@StateID INT = NULL,
@DistID INT = NULL,
@FromDate DATE = NULL,
@ToDate DATE = NULL,
@AgencyCode VARCHAR(10) = NULL
AS
BEGIN
 SET NOCOUNT ON;

 SET @StateID = NULLIF(@StateID, '');
 SET @DistID = NULLIF(@DistID, '');
 SET @AgencyCode = CASE WHEN @AgencyCode = '' OR @AgencyCode is null THEN null Else @AgencyCode END;

 IF OBJECT_ID('tempdb..#PeformanceSCSTReport') IS NOT NULL DROP TABLE #PeformanceSCSTReport
 IF OBJECT_ID('tempdb..#WorkFlowLevel')        IS NOT NULL DROP TABLE #WorkFlowLevel

 DECLARE @ToDateExclusive DATE = CASE WHEN @ToDate IS NULL THEN NULL ELSE DATEADD(DAY,1,@ToDate) END;

 Select * into #PeformanceSCSTReport from (
  SELECT A.ApplID,ApplCode,schemeWrkFlowID,A.SchemeDetID,A.TotalProjectCost,DataSource,
    CASE WHEN (@Level = 'AgencyWise')  THEN AgencyCode WHEN @Level = 'StateWise' THEN State_Name WHEN @Level = 'DistrictWise' THEN DistrictName END LevelCOde
  FROM t_ApplicantData AS A
  WHERE 1 =1 AND IsFinalSub = 1 AND SocialCatID in('SC','ST')
  AND A.FinalSubDate >= @FromDate AND A.FinalSubDate < @ToDateExclusive
  AND AgencyCode = (CASE WHEN Isnull(@AgencyCode,'') <> '' THEN @AgencyCode else AgencyCode END)
  AND StateID = (CASE WHEN Isnull(@StateID,'') <> '' THEN @StateID else stateID END)
  AND DistID = (CASE WHEN Isnull(@DistID,'') <> '' THEN @DistID else DistID END)
  UNION ALL
  Select S.SecLoanApplID AS ApplID,S.ApplCode,S.SchemeWrkFlowID,S.SchemeDetID,s.TotalProjectCost,S.DataSource,
     CASE WHEN (@Level = 'AgencyWise')  THEN S.AgencyCode WHEN @Level = 'StateWise' THEN S.State_Name WHEN @Level = 'DistrictWise' THEN S.DistrictName END LevelCOde
  from t_SecLoanApplData S
  Where 1 =1 AND S.IsFinalSub = 1 AND S.SocialCatID in('SC','ST')
  AND S.FinalSubDate >= @FromDate AND S.FinalSubDate < @ToDateExclusive
  AND S.AgencyCode = (CASE WHEN Isnull(@AgencyCode,'') <> '' THEN @AgencyCode else S.AgencyCode END)
  AND S.StateID = (CASE WHEN Isnull(@StateID,'') <> '' THEN @StateID else S.stateID END)
  AND S.DistID = (CASE WHEN Isnull(@DistID,'') <> '' THEN @DistID else S.DistID END)
 )Tab
 Where LevelCOde is not null
 OPTION (RECOMPILE);

 SELECT SchemeWrkFlowID, StageStatus, Seq
 INTO #WorkFlowLevel
 FROM m_SchemeWrkFlow WITH (NOLOCK);

 CREATE CLUSTERED INDEX IX_WorkFlow
 ON #WorkFlowLevel
 (
  SchemeWrkFlowID
 );

 CREATE NONCLUSTERED INDEX IX_WorkFlowStage
 ON #WorkFlowLevel
 (
  StageStatus,
  Seq
 )
 INCLUDE(SchemeWrkFlowID);

 ;With
 BaseCounts AS (
  SELECT
   P.LevelCode,
   SUM(CASE WHEN (W.Seq >= 5 OR W.Seq = 0)  THEN 1 ELSE 0 END) AS AgencyReceived,
   SUM(CASE WHEN W.StageStatus = 'Return By Agency' AND (P.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(P.Datasource)), '') IS NULL) THEN 1 ELSE 0 END) AS AgencyReturned
  FROM #PeformanceSCSTReport P
  INNER JOIN #WorkFlowLevel W ON P.SchemeWrkFlowID = W.SchemeWrkFlowID
  GROUP BY P.LevelCode
 ),
 FwdToBankCurrYr AS (
  SELECT TAB.LevelCode,COUNT(TAB.ApplID) FwdToBankCurrYr
  FROM (
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN A.AgencyCode WHEN @Level = 'StateWise' THEN A.State_Name WHEN @Level = 'DistrictWise' THEN A.DistrictName END LevelCOde,
     A.ApplID ApplID
   FROM t_ApplicantData A WITH (NOLOCK)
   INNER JOIN #WorkFlowLevel W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
   WHERE ( A.BankForwardDate >= @FromDate AND A.BankForwardDate < @ToDateExclusive ) AND A.SocialCatID in('SC','ST')
   AND W.seq >=6 AND W.StageStatus NOT in ('Return To Applicant','Under Process','Return By Agency','Application Withdrawn at Bank ','Application Withdrawn at Implementing Agency')
    AND (A.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR A.StateID = @StateID)
   AND (@DistID IS NULL OR A.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
   UNION
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN A.AgencyCode WHEN @Level = 'StateWise' THEN A.State_Name WHEN @Level = 'DistrictWise' THEN A.DistrictName END LevelCOde,
     A.SecLoanApplID ApplID
   FROM t_SecLoanApplData A WITH (NOLOCK)
   INNER JOIN #WorkFlowLevel W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
   WHERE  ( A.BankForwardDate >= @FromDate AND A.BankForwardDate < @ToDateExclusive ) AND A.SocialCatID in('SC','ST') AND
    W.seq >=6 AND W.StageStatus NOT in ('Return To Applicant','Under Process','Return By Agency','Application Withdrawn at Bank ','Application Withdrawn at Implementing Agency')
    AND (A.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR A.StateID = @StateID)
   AND (@DistID IS NULL OR A.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
   ) TAB
  GROUP BY TAB.LevelCode
 ),
 SancAndMMInvolved AS (
  SELECT MainTab.LevelCode,
      SUM(MainTab.SancapplCount) AS LoanSancByBankCount,
      SUM(MainTab.MMInvolveAmt) AS LoanSancByBankAmount
  FROM (
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde,
    Count(B.ApplID) AS SancapplCount,
    CAST(SUM(CASE WHEN s.SchemeName = 'Manufacturing'
     THEN ROUND((CASE WHEN b.TotalProjectCost >= 5000000 THEN 5000000 ELSE b.BankTotPrjCost END) / 100000.0,2) * sd.Subsidy / 100.0
     WHEN s.SchemeName = 'Service' THEN ROUND((CASE WHEN b.TotalProjectCost >= 2000000 THEN 2000000 ELSE b.BankTotPrjCost END)/ 100000.0,2) * sd.Subsidy / 100.0
     ELSE 0 END ) AS decimal(18,2)) AS MMInvolveAmt
   FROM t_BankProcess b WITH (NOLOCK)
   JOIN t_ApplicantData a WITH (NOLOCK) ON b.ApplID = a.ApplID
   JOIN m_Scheme s WITH (NOLOCK) ON a.SchemeID = s.SchemeID
   JOIN m_SchemeDet sd WITH (NOLOCK) ON sd.SchemeID = s.SchemeID AND sd.SchemeDetID = a.SchemeDetID
   WHERE s.SchemeID IN (1,2) AND b.SancDate >=@FromDate AND b.SancDate < @ToDateExclusive AND (a.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR a.StateID = @StateID) AND a.SocialCatID in('SC','ST')
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
   GROUP BY CASE
     WHEN (@Level ='AgencyWise' OR @Level ='ImpleAgency') THEN a.AgencyCode
     WHEN @Level ='StateWise' THEN a.State_Name
     WHEN @Level ='DistrictWise' THEN a.DistrictName
    END
   UNION ALL
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a1.AgencyCode WHEN @Level = 'StateWise' THEN a1.State_Name WHEN @Level = 'DistrictWise' THEN a1.DistrictName END LevelCOde,
    Count(B.SecLoanApplID) AS SancapplCount,
    CAST(SUM(CASE WHEN s.SchemeName = 'Manufacturing'
     THEN ROUND((CASE WHEN b.TotalProjectCost >= 5000000 THEN 5000000 ELSE b.BankTotPrjCost END) / 100000.0,2) * sd.Subsidy / 100.0
     WHEN s.SchemeName = 'Service' THEN ROUND((CASE WHEN b.TotalProjectCost >= 2000000 THEN 2000000 ELSE b.BankTotPrjCost END)/ 100000.0,2) * sd.Subsidy / 100.0
     ELSE 0 END ) AS decimal(18,2)) AS MMInvolveAmt
   FROM t_SecLoanBankProcess b WITH (NOLOCK)
   INNER JOIN t_SecLoanApplData a1   WITH (NOLOCK) ON b.SecLoanApplID = a1.SecLoanApplID
   JOIN m_Scheme s WITH (NOLOCK) ON a1.SchemeID = s.SchemeID
   JOIN m_SchemeDet sd WITH (NOLOCK) ON sd.SchemeID = s.SchemeID AND sd.SchemeDetID = a1.SchemeDetID
   WHERE s.SchemeID IN (3,4) AND b.SancDate >=@FromDate AND b.SancDate < @ToDateExclusive AND (a1.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a1.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR a1.StateID = @StateID)
   AND (@DistID IS NULL OR a1.DistID = @DistID) AND a1.SocialCatID in('SC','ST')
   AND (@AgencyCode IS NULL OR a1.AgencyCode = @AgencyCode)
   GROUP BY CASE
     WHEN (@Level ='AgencyWise' OR @Level ='ImpleAgency') THEN a1.AgencyCode
     WHEN @Level ='StateWise' THEN a1.State_Name
     WHEN @Level ='DistrictWise' THEN a1.DistrictName
    END
  ) MainTab
  GROUP BY MainTab.LevelCode
 ),
 EDPTrainingUndergone AS (
  SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a1.AgencyCode WHEN @Level = 'StateWise' THEN a1.State_Name WHEN @Level = 'DistrictWise' THEN a1.DistrictName END LevelCOde,
    SUM(CASE WHEN e.CertIssueDate >= @FromDate AND e.CertIssueDate < @ToDateExclusive THEN 1 ELSE 0 END) AS EDPTrainingUndergone
  FROM t_ApplicantData a1   WITH (NOLOCK)
  INNER JOIN t_BankProcess B WITH (NOLOCK) ON a1.ApplID = B.ApplID
  INNER JOIN t_EdpTraining e WITH (NOLOCK) ON a1.ApplID = e.ApplID
  Inner Join m_SchemeWrkFlow s ON B.WrkFlowID = S.SchemeWrkFlowID
  where A1.SchemeID IN (1,2) AND (a1.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a1.Datasource)), '') IS NULL)
  AND (@StateID IS NULL OR a1.StateID = @StateID)
  AND (@DistID IS NULL OR a1.DistID = @DistID) AND a1.SocialCatID in('SC','ST')
  AND (@AgencyCode IS NULL OR a1.AgencyCode = @AgencyCode)
  ANd S.Seq >=9
  GROUP BY CASE
    WHEN (@Level ='AgencyWise' OR @Level ='ImpleAgency') THEN a1.AgencyCode
    WHEN @Level ='StateWise' THEN a1.State_Name
    WHEN @Level ='DistrictWise' THEN a1.DistrictName
   END
 ),
 MMClaimedApplSum AS (
  SELECT MainTab.LevelCode,
      SUM(MainTab.MMClaimedApplCount) AS MMClaimedCount,
      SUM(MainTab.MMClaimedAmount) AS MMClaimedAmount
  FROM (
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde,
       Count(B.ApplID) AS MMClaimedApplCount,
       CAST(SUM(ROUND(MMClaimed/ 100000.0,2)) AS decimal(18,2))AS MMClaimedAmount
   FROM t_BankProcess b WITH (NOLOCK)
   JOIN t_ApplicantData a WITH (NOLOCK) ON b.ApplID = a.ApplID
   WHERE  (a.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND b.MMClaimDt >=@FromDate AND b.MMClaimDt < @ToDateExclusive
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
   AND b.MMClaimDt IS NOT NULL AND a.SchemeID IN (1,2)  AND a.SocialCatID in('SC','ST')
   GROUP BY CASE
     WHEN (@Level ='AgencyWise' OR @Level ='ImpleAgency') THEN a.AgencyCode
     WHEN @Level ='StateWise' THEN a.State_Name
     WHEN @Level ='DistrictWise' THEN a.DistrictName
    END
   UNION ALL
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde,
       Count(B.SecLoanApplID) AS MMClaimedApplCount,
       CAST(SUM(ROUND(MMClaimed/ 100000.0,2)) AS decimal(18,2)) AS MMClaimedAmount
   FROM t_SecLoanBankProcess b WITH (NOLOCK)
   JOIN t_SecLoanApplData a WITH (NOLOCK) ON b.SecLoanApplID = a.SecLoanApplID
   WHERE b.MMClaimDt >=@FromDate AND b.MMClaimDt < @ToDateExclusive  AND (a.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
   AND b.MMClaimDt IS NOT NULL AND a.SocialCatID in('SC','ST')
   GROUP BY CASE
     WHEN (@Level ='AgencyWise' OR @Level ='ImpleAgency') THEN a.AgencyCode
     WHEN @Level ='StateWise' THEN a.State_Name
     WHEN @Level ='DistrictWise' THEN a.DistrictName
    END
  ) MainTab
  GROUP BY MainTab.LevelCode
 ),
 MMDisbCombined AS (
  SELECT MainTab.LevelCode,
      SUM(MainTab.ApplCountVal) AS MMDisbursedCount,
      SUM(MainTab.MMAmountVal) AS MMDisbursedAmount
  FROM (
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde,
       Count(B.ApplID) AS ApplCountVal,
       CAST(SUM(ROUND(MMAmount/ 100000.0,2)) AS decimal(18,2)) AS MMAmountVal
   FROM t_BankProcess b WITH (NOLOCK)
   JOIN t_ApplicantData a WITH (NOLOCK) ON b.ApplID = a.ApplID
   INNER JOIN t_NodalBankMMClaimed M WITH (NOLOCK) ON B.ApplID = M.ApplID
   WHERE b.MMTrxfDate >=@FromDate AND b.MMTrxfDate < @ToDateExclusive AND b.MMTrxfDate is not null
   AND IsMMProcessed = 1
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
   AND a.SchemeID IN (1,2)   AND a.SocialCatID in('SC','ST')
   GROUP BY CASE
     WHEN (@Level ='AgencyWise' OR @Level ='ImpleAgency') THEN a.AgencyCode
     WHEN @Level ='StateWise' THEN a.State_Name
     WHEN @Level ='DistrictWise' THEN a.DistrictName
    END
   UNION ALL
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde,
        Count(B.SecLoanApplID) AS ApplCountVal,
        CAST(SUM(ROUND(MMAmount/ 100000.0,2)) AS decimal(18,2)) AS MMAmountVal
   FROM t_SecLoanBankProcess b WITH (NOLOCK)
   JOIN t_SecLoanApplData a WITH (NOLOCK) ON b.SecLoanApplID = a.SecLoanApplID
   INNER JOIN t_SecNodalBankMMClaimed M WITH (NOLOCK) ON B.SecLoanApplID = M.SecLoanApplID
   WHERE b.MMTrxfDate >=@FromDate AND b.MMTrxfDate < @ToDateExclusive  AND b.MMTrxfDate is not null AND IsMMProcessed = 1
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)  AND a.SocialCatID in('SC','ST')
   GROUP BY CASE
     WHEN (@Level ='AgencyWise' OR @Level ='ImpleAgency') THEN a.AgencyCode
     WHEN @Level ='StateWise' THEN a.State_Name
     WHEN @Level ='DistrictWise' THEN a.DistrictName
    END
  ) MainTab
  GROUP BY MainTab.LevelCode
 ),
 TDRCount AS (
  SELECT MainTab.LevelCOde, Count(MainTab.TDRApplCount) AS TDRCount
  FROM (
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde,
       B.ApplID AS TDRApplCount
   FROM t_BankProcess b WITH (NOLOCK)
   JOIN t_ApplicantData a WITH (NOLOCK) ON b.ApplID = a.ApplID
   WHERE a.SchemeID IN (1,2)  AND b.TDRDepDate IS nOT  NULL
   AND b.MMTrxfDate >=@FromDate AND b.MMTrxfDate < @ToDateExclusive  AND (a.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode) AND a.SocialCatID in('SC','ST')
   UNION ALL
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde,
       B.SecLoanApplID AS TDRApplCount
   FROM t_SecLoanBankProcess b WITH (NOLOCK)
   JOIN t_SecLoanApplData a ON b.SecLoanApplID = a.SecLoanApplID
   WHERE A.SchemeID IN (3,4) AND B.TDRDepDate IS NOT  NULL
   AND b.MMTrxfDate >=@FromDate AND b.MMTrxfDate < @ToDateExclusive  AND (a.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode) AND a.SocialCatID in('SC','ST')
  ) MainTab
  GROUP BY MainTab.LevelCOde
 ),
 PendingEDPTrainingCount AS (
  SELECT  MainTab.LevelCOde,Sum(EdpPenApplCount)PendingEDPTrainingCount FROM (
    SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a1.AgencyCode WHEN @Level = 'StateWise' THEN a1.State_Name WHEN @Level = 'DistrictWise' THEN a1.DistrictName END LevelCOde,
      SUM(CASE WHEN e.CertIssueDate IS NULL AND B.SancDate >= @FromDate AND B.SancDate < @ToDateExclusive THEN 1 ELSE 0 END) AS EdpPenApplCount
    FROM t_ApplicantData a1   WITH (NOLOCK)
    INNER JOIN t_BankProcess B WITH (NOLOCK) ON a1.ApplID = B.ApplID
    INNER JOIN t_EdpTraining e WITH (NOLOCK) ON a1.ApplID = e.ApplID
    Inner Join m_SchemeWrkFlow s ON B.WrkFlowID = S.SchemeWrkFlowID
    where A1.SchemeID IN (1,2) AND (a1.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a1.Datasource)), '') IS NULL)
    AND (@StateID IS NULL OR a1.StateID = @StateID)
    AND (@DistID IS NULL OR a1.DistID = @DistID)  AND a1.SocialCatID in('SC','ST')
    AND (@AgencyCode IS NULL OR a1.AgencyCode = @AgencyCode)
    ANd S.Seq >=9  AND a1.SocialCatID in('SC','ST')
    GROUP BY CASE
      WHEN (@Level ='AgencyWise' OR @Level ='ImpleAgency') THEN a1.AgencyCode
      WHEN @Level ='StateWise' THEN a1.State_Name
      WHEN @Level ='DistrictWise' THEN a1.DistrictName
     END
  )MainTab
  GROUP BY MainTab.LevelCode
 ),
 BankReturn AS (
 SELECT TAB.LevelCode,COUNT(TAB.ApplID) AS BankReturn
  FROM (
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN A.AgencyCode WHEN @Level = 'StateWise' THEN A.State_Name WHEN @Level = 'DistrictWise' THEN A.DistrictName END LevelCOde,
     A.ApplID ApplID
   FROM t_ApplicantData A WITH (NOLOCK)
   Inner Join t_BankProcess B  WITH (NOLOCK) ON A.ApplID = B.ApplID
   INNER JOIN #WorkFlowLevel W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
   WHERE ( A.BankForwardDate >=@FromDate AND A.BankForwardDate < @ToDateExclusive )
   AND W.StageStatus in ('Rejected / Return')
   AND (A.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR A.StateID = @StateID)
   AND (@DistID IS NULL OR A.DistID = @DistID)
   AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode) AND A.SocialCatID in('SC','ST')
   UNION
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN A.AgencyCode WHEN @Level = 'StateWise' THEN A.State_Name WHEN @Level = 'DistrictWise' THEN A.DistrictName END LevelCOde,
     A.SecLoanApplID ApplID
   FROM t_SecLoanApplData A WITH (NOLOCK)
   INNER JOIN #WorkFlowLevel W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
   Inner Join t_BankProcess B  WITH (NOLOCK) ON A.ApplID = B.ApplID
   WHERE ( A.BankForwardDate >=@FromDate AND A.BankForwardDate < @ToDateExclusive ) AND
   W.StageStatus  in ('Rejected / Return')
   AND (A.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR A.StateID = @StateID)
   AND (@DistID IS NULL OR A.DistID = @DistID)
   AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode)  AND A.SocialCatID in('SC','ST')
  ) TAB
  GROUP BY TAB.LevelCode
 ),
 AppPendindAtBankMMAmt AS (
 SELECT MainTab.LevelCode,
      SUM(MainTab.ApplCountVal) AS BankPendingApplCount,
      SUM(MainTab.BankTotPrjCost) AS AppPendindAtBankMMAmt
  FROM (
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde,
       Count(a.ApplID) AS ApplCountVal,
       CAST(ROUND(SUM(CASE WHEN s.SchemeName = 'Manufacturing'
     THEN (CASE WHEN a.TotalProjectCost >= 5000000 THEN 5000000 ELSE a.TotalProjectCost END) * sd.Subsidy / 100.0
     WHEN s.SchemeName = 'Service' THEN (CASE WHEN a.TotalProjectCost >= 2000000 THEN 2000000 ELSE a.TotalProjectCost END) * sd.Subsidy / 100.0
     ELSE 0 END ) / 100000.0,2) AS decimal(18,2)) AS BankTotPrjCost
   FROM t_ApplicantData a WITH (NOLOCK)
   JOIN m_SchemeWrkFlow s1 WITH (NOLOCK) ON a.SchemeWrkFlowID = s1.SchemeWrkFlowID
   JOIN m_Scheme s WITH (NOLOCK) ON a.SchemeID = s.SchemeID
   JOIN m_SchemeDet sd WITH (NOLOCK) ON sd.SchemeID = s.SchemeID AND sd.SchemeDetID = a.SchemeDetID
   WHERE s1.seq >=6 AND s1.StageStatus NOT in ('Return To Applicant','Under Process','Return By Agency')  AND (a.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND A.BankForwardDate >=@FromDate AND A.BankForwardDate < @ToDateExclusive
   ANd s1.StageStatus in ('Further Process','Forwarded To Primary Bank') AND a.SocialCatID in('SC','ST')
   AND A.ApplCode Not IN (
    Select A.ApplCode from t_ApplicantData A WITH (NOLOCK)
    INNER JOIN m_SchemeWrkFlow W WITH (NOLOCK) ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
    where W.StageStatus  in ('Return To Applicant By Bank') AND (A.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
    AND (@StateID IS NULL OR A.StateID = @StateID)
    AND (@DistID IS NULL OR A.DistID = @DistID)
    AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode)  AND A.SocialCatID in('SC','ST')
    Union
    Select A.ApplCode from t_ApplicantData A WITH (NOLOCK)
    INNER JOIN m_SchemeWrkFlow W WITH (NOLOCK) ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
    Inner join t_BankProcess B WITH (NOLOCK) ON A.ApplID = b.ApplID
    where  b.SancDate >=@FromDate AND b.SancDate < @ToDateExclusive  AND (A.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
    AND (@StateID IS NULL OR A.StateID = @StateID)
    AND (@DistID IS NULL OR A.DistID = @DistID)
    AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode) AND A.SocialCatID in('SC','ST')
   )
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
   GROUP BY CASE
     WHEN (@Level ='AgencyWise' OR @Level ='ImpleAgency') THEN a.AgencyCode
     WHEN @Level ='StateWise' THEN a.State_Name
     WHEN @Level ='DistrictWise' THEN a.DistrictName
    END
   UNION ALL
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde,
       Count(a.SecLoanApplID) AS ApplCountVal,
       CAST(ROUND(SUM(CASE WHEN s.SchemeName = 'Manufacturing'
     THEN (CASE WHEN a.TotalProjectCost >= 5000000 THEN 5000000 ELSE a.TotalProjectCost END) * sd.Subsidy / 100.0
     WHEN s.SchemeName = 'Service' THEN (CASE WHEN a.TotalProjectCost >= 2000000 THEN 2000000 ELSE a.TotalProjectCost END) * sd.Subsidy / 100.0
     ELSE 0 END ) / 100000.0,2) AS decimal(18,2)) AS BankTotPrjCost
   FROM t_SecLoanApplData a WITH (NOLOCK)
   JOIN m_SchemeWrkFlow s1 ON a.SchemeWrkFlowID = S1.SchemeWrkFlowID
   JOIN m_Scheme s WITH (NOLOCK) ON a.SchemeID = s.SchemeID
   JOIN m_SchemeDet sd WITH (NOLOCK) ON sd.SchemeID = s.SchemeID AND sd.SchemeDetID = a.SchemeDetID
   WHERE  s1.seq >=6 AND s1.StageStatus NOT in ('Return To Applicant','Under Process','Return By Agency')
   AND (a.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND A.BankForwardDate >=@FromDate AND A.BankForwardDate < @ToDateExclusive
   ANd s1.StageStatus in ('Further Process','Forwarded To Primary Bank') AND a.SocialCatID in('SC','ST')
   AND SecApplCode Not IN (
    Select SecApplCode from t_SecLoanApplData A
    INNER JOIN m_SchemeWrkFlow W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
    where W.StageStatus  in ('Return To Applicant By Bank')  AND (A.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
    AND (@StateID IS NULL OR A.StateID = @StateID)
    AND (@DistID IS NULL OR A.DistID = @DistID)
    AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode) AND A.SocialCatID in('SC','ST')
    Union
    Select SecApplCode from t_SecLoanApplData A
    INNER JOIN m_SchemeWrkFlow W ON A.SchemeWrkFlowID = W.SchemeWrkFlowID
    Inner join t_SecLoanBankProcess B ON A.SecLoanApplID = b.SecLoanApplID
    where  b.SancDate >=@FromDate AND b.SancDate < @ToDateExclusive  AND (A.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(A.Datasource)), '') IS NULL)
    AND (@StateID IS NULL OR A.StateID = @StateID)
    AND (@DistID IS NULL OR A.DistID = @DistID)
    AND (@AgencyCode IS NULL OR A.AgencyCode = @AgencyCode) AND A.SocialCatID in('SC','ST')
   )
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode) AND a.SocialCatID in('SC','ST')
   GROUP BY CASE
     WHEN (@Level ='AgencyWise' OR @Level ='ImpleAgency') THEN a.AgencyCode
     WHEN @Level ='StateWise' THEN a.State_Name
     WHEN @Level ='DistrictWise' THEN a.DistrictName
    END
  ) MainTab
  GROUP BY MainTab.LevelCode
 ),
 PendingMMDisbursementCount AS (
  SELECT MainTab.LevelCode,
      SUM(MainTab.ApplCountVal) AS PendingMMDisbursementCount,
      SUM(MainTab.BankTotPrjCost) AS PendingMMDisbursementTotal
  FROM (
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde,
       CAST(SUM(ROUND(MMClaimed/ 100000.0,2)) AS decimal(18,2)) AS BankTotPrjCost,
       Count(B.ApplID) AS ApplCountVal
   FROM t_BankProcess b WITH (NOLOCK)
   JOIN t_ApplicantData a WITH (NOLOCK) ON b.ApplID = a.ApplID
   JOIN m_Scheme s WITH (NOLOCK) ON a.SchemeID = s.SchemeID
   JOIN m_SchemeDet sd WITH (NOLOCK) ON sd.SchemeID = s.SchemeID AND sd.SchemeDetID = a.SchemeDetID
   WHERE s.SchemeID IN (1,2) AND b.MMClaimDt >=@FromDate AND b.MMClaimDt < @ToDateExclusive  AND (a.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode) AND a.SocialCatID in('SC','ST')
   AND b.MMTrxfDate IS NULL
   GROUP BY CASE
     WHEN (@Level ='AgencyWise' OR @Level ='ImpleAgency') THEN a.AgencyCode
     WHEN @Level ='StateWise' THEN a.State_Name
     WHEN @Level ='DistrictWise' THEN a.DistrictName
    END
   UNION ALL
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde,
       CAST(SUM(ROUND(MMClaimed/ 100000.0,2)) AS decimal(18,2)) AS BankTotPrjCost,
       Count(B.SecLoanApplID) AS ApplCountVal
   FROM t_SecLoanBankProcess b WITH (NOLOCK)
   JOIN t_SecLoanApplData a   WITH (NOLOCK)  ON b.SecLoanApplID = a.SecLoanApplID
   JOIN m_Scheme s WITH (NOLOCK) ON a.SchemeID = s.SchemeID
   JOIN m_SchemeDet sd WITH (NOLOCK) ON sd.SchemeID = s.SchemeID AND sd.SchemeDetID = a.SchemeDetID
   WHERE s.SchemeID IN (3,4) AND b.MMClaimDt >=@FromDate AND b.MMClaimDt < @ToDateExclusive  AND (a.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode) AND a.SocialCatID in('SC','ST')
   AND b.MMTrxfDate IS NULL
   GROUP BY CASE
     WHEN (@Level ='AgencyWise' OR @Level ='ImpleAgency') THEN a.AgencyCode
     WHEN @Level ='StateWise' THEN a.State_Name
     WHEN @Level ='DistrictWise' THEN a.DistrictName
    END
  ) MainTab
  GROUP BY MainTab.LevelCode
 ),
 PendingMMDisbursementNoOfPrjrefRectification AS (
 SELECT MainTab.LevelCode,
     count(MainTab.RectificationApplCount) AS PendingMMDisbursementNoOfPrjrefRectification
 FROM (
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde, A.applid RectificationApplCount
   From t_ApplicantData A WITH (NOLOCK)
   INNER JOIN t_BankProcess b ON A.ApplID = B.ApplID
   Where A.isRefferedImplementingOrBankByValidation = 1
   AND b.MMClaimDt >=@FromDate AND b.MMClaimDt < @ToDateExclusive
   AND b.MMClaimDt is not null AND A.isUnderValidation =0 AND A.isUnderVarification =0
   AND (a.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)
   UNION All
   SELECT CASE WHEN (@Level = 'AgencyWise' OR @Level ='ImpleAgency')  THEN a.AgencyCode WHEN @Level = 'StateWise' THEN a.State_Name WHEN @Level = 'DistrictWise' THEN a.DistrictName END LevelCOde,
     A.SecLoanApplID RectificationApplCount
   From t_SecLoanApplData A  WITH (NOLOCK)
   INNER JOIN t_BankProcess b ON A.ApplID = B.ApplID
   Where A.isRetBackByVali = 1
   AND b.MMClaimDt >=@FromDate AND b.MMClaimDt < @ToDateExclusive
   AND b.MMClaimDt is not null AND A.isUnderValidation =0 AND A.isUnderVarification =0
    AND (a.DataSource IN ('MIG' ,'MIG1','MIG2') OR NULLIF(LTRIM(RTRIM(a.Datasource)), '') IS NULL)
   AND (@StateID IS NULL OR a.StateID = @StateID)
   AND (@DistID IS NULL OR a.DistID = @DistID)
   AND (@AgencyCode IS NULL OR a.AgencyCode = @AgencyCode)   AND a.SocialCatID in('SC','ST')
  ) MainTab
  GROUP BY MainTab.LevelCode
 ),
 AllLevelCodes AS (
  SELECT LevelCode FROM BaseCounts
  UNION
  SELECT LevelCode FROM FwdToBankCurrYr
  UNION
  SELECT LevelCode FROM SancAndMMInvolved
  UNION
  SELECT LevelCode FROM EDPTrainingUndergone
  UNION
  SELECT LevelCode FROM MMClaimedApplSum
  UNION
  SELECT LevelCode FROM MMDisbCombined
  UNION
  SELECT LevelCode FROM TDRCount
  UNION
  SELECT LevelCode FROM PendingEDPTrainingCount
  Union
  SELECT Levelcode From BankReturn
  Union
  SELECT LevelCode FROM AppPendindAtBankMMAmt
  Union
  SELECT LevelCode FROM PendingMMDisbursementCount
  Union
  SELECT LevelCode FROM PendingMMDisbursementNoOfPrjrefRectification
 )
 SELECT
  LC.LevelCode AS LevelCode,
  COALESCE(BC.AgencyReceived, 0) AS AgencyReceived,
  COALESCE(BC.AgencyReturned, 0) AS AgencyReturned,
  COALESCE(FA.FwdToBankCurrYr, 0) AS FwdToBankCurrYr,
  COALESCE(SMI.LoanSancByBankCount, 0) AS LoanSancByBankCount,
  COALESCE(SMI.LoanSancByBankAmount, 0) AS LoanSancByBankAmount,
  COALESCE(EC.EDPTrainingUndergone, 0) AS EDPTrainingUndergone,
  COALESCE(mcas.MMClaimedCount, 0) AS MMClaimedCount,
  COALESCE(MCAS.MMClaimedAmount, 0) AS MMClaimedAmount,
  COALESCE(MDC.MMDisbursedCount, 0) AS MMDisbursedCount,
  COALESCE(MDC.MMDisbursedAmount, 0) AS MMDisbursedAmount,
  COALESCE(TDR.TDRCount, 0) AS TDRCount,
  COALESCE(PEDPTC.PendingEDPTrainingCount, 0) AS PendingEDPTrainingCount,
  COALESCE(BankReturn.BankReturn,0)BankReturn,
  COALESCE(PABC.AppPendindAtBankMMAmt,0)AppPendindAtBankMMAmt,
  COALESCE(PendingMMDisbursementCount.PendingMMDisbursementCount,0)PendingMMDisbursementCount,
  COALESCE(PendingMMDisbursementNoOfPrjrefRectification.PendingMMDisbursementNoOfPrjrefRectification,0)PendingMMDisbursementNoOfPrjrefRectification,
  COALESCE(PendingMMDisbursementCount.PendingMMDisbursementTotal,0)PendingMMDisbursementTotal
 FROM AllLevelCodes LC
 LEFT JOIN BaseCounts BC ON (LC.LevelCode = BC.LevelCode OR (LC.LevelCode IS NULL AND BC.LevelCode IS NULL))
 LEFT JOIN FwdToBankCurrYr FA ON (LC.LevelCode = FA.LevelCode OR (LC.LevelCode IS NULL AND FA.LevelCode IS NULL))
 LEFT JOIN SancAndMMInvolved SMI ON (LC.LevelCode = SMI.LevelCode OR (LC.LevelCode IS NULL AND SMI.LevelCode IS NULL))
 LEFT JOIN EDPTrainingUndergone EC ON (LC.LevelCode = EC.LevelCode OR (LC.LevelCode IS NULL AND EC.LevelCode IS NULL))
 LEFT JOIN MMClaimedApplSum MCAS ON (LC.LevelCode = MCAS.LevelCode OR (LC.LevelCode IS NULL AND MCAS.LevelCode IS NULL))
 LEFT JOIN MMDisbCombined MDC ON (LC.LevelCode = MDC.LevelCode OR (LC.LevelCode IS NULL AND MDC.LevelCode IS NULL))
 LEFT JOIN PendingEDPTrainingCount PEDPTC ON (LC.LevelCode = PEDPTC.LevelCode OR (LC.LevelCode IS NULL AND PEDPTC.LevelCode IS NULL))
 LEFT JOIN TDRCount TDR ON (LC.LevelCode = TDR.LevelCode OR (LC.LevelCode IS NULL AND TDR.LevelCode IS NULL))
 LEFT JOIN AppPendindAtBankMMAmt PABC ON (LC.LevelCode = PABC.LevelCode OR (LC.LevelCode IS NULL AND PABC.LevelCode IS NULL))
 LEFT JOIN PendingMMDisbursementCount ON (LC.LevelCode = PendingMMDisbursementCount.LevelCode OR (LC.LevelCode IS NULL AND PendingMMDisbursementCount.LevelCode IS NULL))
 LEFT OUTER JOIN PendingMMDisbursementNoOfPrjrefRectification ON (LC.LevelCode = PendingMMDisbursementNoOfPrjrefRectification.LevelCode OR (LC.LevelCode IS NULL AND PendingMMDisbursementNoOfPrjrefRectification.LevelCode IS NULL))
 LEFT OUTER JOIN BankReturn  ON (LC.LevelCode = BankReturn.LevelCode OR (LC.LevelCode IS NULL AND BankReturn.LevelCode IS NULL))
 OPTION (RECOMPILE);
END
