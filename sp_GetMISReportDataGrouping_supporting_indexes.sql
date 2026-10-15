/* =====================================================================================
   Supporting indexes for sp_GetMISReportDataGrouping (optimized version)

   WHY THIS SCRIPT EXISTS
   ----------------------
   The optimized proc was made SARGable: every "CAST(col AS DATE) >= @FromDate ..."
   was rewritten to "col >= @FromDate AND col < @ToDateExclusive". That rewrite only
   pays off if an index exists on the date column so SQL Server can SEEK the range
   instead of SCANning the whole table. These indexes are that other half of the win.

   Each metric block in the proc filters a different date column on the big tables
   (t_ApplicantData, t_BankProcess, and the Sec-loan equivalents), so one index per
   driving date column is created, with INCLUDE columns chosen to make the common
   lookups covering (no key lookups back to the base table).

   HOW TO USE
   ----------
   - Review with your DBA before running in production. Index choices depend on your
     real data distribution, existing indexes, write load and disk.
   - Run OUTSIDE the proc, once. Creating indexes is a one-time DDL operation.
   - Guards (IF NOT EXISTS) make the script safe to re-run; nothing is dropped.
   - If you are on Enterprise Edition, you may add WITH (ONLINE = ON) to each CREATE
     to avoid blocking; it is left off here for broad compatibility.
   - After creating these, UPDATE STATISTICS (or let auto-stats run) and re-check the
     actual execution plan to confirm seeks replaced scans.

   NOTE on INCLUDE lists: trim any column you do not filter/group on in your build to
   keep the indexes narrow. The lists below cover the predicates the proc actually uses.
   ===================================================================================== */

SET NOCOUNT ON;
GO

/* -------------------------------------------------------------------------------------
   t_ApplicantData
   Driving date columns used by different blocks: FinalSubDate, BankForwardDate,
   WithdrawDate. The INCLUDE list carries the optional-parameter filter columns and the
   grouping/zone columns so the seeks stay covering.
   ------------------------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_ApplicantData_FinalSubDate' AND object_id = OBJECT_ID('dbo.t_ApplicantData'))
CREATE NONCLUSTERED INDEX IX_ApplicantData_FinalSubDate
ON dbo.t_ApplicantData (FinalSubDate)
INCLUDE (ApplID, ApplCode, IsFinalSub, StateID, DistID, Gender, SocialCatID, SchemeID,
         SchemeDetID, SchemeWrkFlowID, AgencyCode, AgencyOffID, UnitLocation,
         FinBankID1, FinBankID2, IsUnderAlternativeBank, DataSource, State_Name,
         DistrictName, TotalProjectCost, isRefferedImplementingOrBankByValidation);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_ApplicantData_BankForwardDate' AND object_id = OBJECT_ID('dbo.t_ApplicantData'))
CREATE NONCLUSTERED INDEX IX_ApplicantData_BankForwardDate
ON dbo.t_ApplicantData (BankForwardDate)
INCLUDE (ApplID, ApplCode, SchemeWrkFlowID, AgencyOffID, AgencyCode, StateID, DistID,
         Gender, SocialCatID, SchemeID, UnitLocation, FinBankID1, DataSource,
         State_Name, DistrictName);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_ApplicantData_WithdrawDate' AND object_id = OBJECT_ID('dbo.t_ApplicantData'))
CREATE NONCLUSTERED INDEX IX_ApplicantData_WithdrawDate
ON dbo.t_ApplicantData (WithdrawDate)
INCLUDE (ApplID, SchemeWrkFlowID, AgencyOffID, AgencyCode, StateID, DistID, Gender,
         SocialCatID, SchemeID, UnitLocation, FinBankID1, DataSource, State_Name,
         DistrictName);
GO

/* Join/lookup support: proc repeatedly joins t_BankProcess/EDP back to ApplID, and
   resolves ApplCode <-> ApplID for the Sec-loan join. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_ApplicantData_ApplCode_ApplID' AND object_id = OBJECT_ID('dbo.t_ApplicantData'))
CREATE NONCLUSTERED INDEX IX_ApplicantData_ApplCode_ApplID
ON dbo.t_ApplicantData (ApplCode, ApplID)
INCLUDE (AgencyOffID, SchemeID, Gender, FinBankID1, DataSource, State_Name, DistrictName);
GO

/* -------------------------------------------------------------------------------------
   t_BankProcess
   Driving date columns: SancDate, MMClaimDt, MMTrxfDate, plus TDRDepDate for the TDR
   block (filtered by MMTrxfDate but also TDRDepDate IS NOT NULL).
   ------------------------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_BankProcess_SancDate' AND object_id = OBJECT_ID('dbo.t_BankProcess'))
CREATE NONCLUSTERED INDEX IX_BankProcess_SancDate
ON dbo.t_BankProcess (SancDate)
INCLUDE (ApplID, TotalProjectCost, WrkFlowID);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_BankProcess_MMClaimDt' AND object_id = OBJECT_ID('dbo.t_BankProcess'))
CREATE NONCLUSTERED INDEX IX_BankProcess_MMClaimDt
ON dbo.t_BankProcess (MMClaimDt)
INCLUDE (ApplID, MMClaimed, MMTrxfDate, isUnderValidation, isUnderVarification);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_BankProcess_MMTrxfDate' AND object_id = OBJECT_ID('dbo.t_BankProcess'))
CREATE NONCLUSTERED INDEX IX_BankProcess_MMTrxfDate
ON dbo.t_BankProcess (MMTrxfDate)
INCLUDE (ApplID, MMAmount, TDRDepDate);
GO

/* -------------------------------------------------------------------------------------
   t_SecLoanApplData  (mirrors t_ApplicantData)
   ------------------------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SecLoanApplData_FinalSubDate' AND object_id = OBJECT_ID('dbo.t_SecLoanApplData'))
CREATE NONCLUSTERED INDEX IX_SecLoanApplData_FinalSubDate
ON dbo.t_SecLoanApplData (FinalSubDate)
INCLUDE (SecLoanApplID, ApplID, ApplCode, SecApplCode, IsFinalSub, StateID, DistID,
         SocialCatID, SchemeID, SchemeDetID, SchemeWrkFlowID, AgencyCode, UnitLocation,
         DataSource, State_Name, DistrictName, TotalProjectCost, isRetBackByVali);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SecLoanApplData_BankForwardDate' AND object_id = OBJECT_ID('dbo.t_SecLoanApplData'))
CREATE NONCLUSTERED INDEX IX_SecLoanApplData_BankForwardDate
ON dbo.t_SecLoanApplData (BankForwardDate)
INCLUDE (SecLoanApplID, ApplID, SecApplCode, SchemeWrkFlowID, AgencyCode, StateID,
         DistID, SocialCatID, SchemeID, UnitLocation, DataSource, State_Name, DistrictName);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SecLoanApplData_ModifyOn' AND object_id = OBJECT_ID('dbo.t_SecLoanApplData'))
CREATE NONCLUSTERED INDEX IX_SecLoanApplData_ModifyOn
ON dbo.t_SecLoanApplData (ModifyOn)
INCLUDE (SecLoanApplID, SchemeWrkFlowID, AgencyCode, StateID, DistID, SocialCatID,
         SchemeID, UnitLocation, DataSource, State_Name, DistrictName);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SecLoanApplData_SecLoanApplID' AND object_id = OBJECT_ID('dbo.t_SecLoanApplData'))
CREATE NONCLUSTERED INDEX IX_SecLoanApplData_SecLoanApplID
ON dbo.t_SecLoanApplData (SecLoanApplID)
INCLUDE (ApplID, SecApplCode, SchemeID, SchemeDetID, SchemeWrkFlowID, AgencyCode,
         DataSource, State_Name, DistrictName, TotalProjectCost);
GO

/* -------------------------------------------------------------------------------------
   t_SecLoanBankProcess
   ------------------------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SecLoanBankProcess_SancDate' AND object_id = OBJECT_ID('dbo.t_SecLoanBankProcess'))
CREATE NONCLUSTERED INDEX IX_SecLoanBankProcess_SancDate
ON dbo.t_SecLoanBankProcess (SancDate)
INCLUDE (SecLoanApplID);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SecLoanBankProcess_MMClaimDt' AND object_id = OBJECT_ID('dbo.t_SecLoanBankProcess'))
CREATE NONCLUSTERED INDEX IX_SecLoanBankProcess_MMClaimDt
ON dbo.t_SecLoanBankProcess (MMClaimDt)
INCLUDE (SecLoanApplID, MMClaimed, MMTrxfDate);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SecLoanBankProcess_MMTrxfDate' AND object_id = OBJECT_ID('dbo.t_SecLoanBankProcess'))
CREATE NONCLUSTERED INDEX IX_SecLoanBankProcess_MMTrxfDate
ON dbo.t_SecLoanBankProcess (MMTrxfDate)
INCLUDE (SecLoanApplID, MMAmount, TDRDepDate);
GO

/* -------------------------------------------------------------------------------------
   t_EdpTraining  (joined by ApplID, filtered by CertIssueDate)
   ------------------------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_EdpTraining_ApplID' AND object_id = OBJECT_ID('dbo.t_EdpTraining'))
CREATE NONCLUSTERED INDEX IX_EdpTraining_ApplID
ON dbo.t_EdpTraining (ApplID)
INCLUDE (TrainingMode, EdpCentreID, CertIssueDate);
GO

/* -------------------------------------------------------------------------------------
   t_NodalBankMMClaimed / t_SecNodalBankMMClaimed  (MMDisb join, filtered IsMMProcessed=1)
   ------------------------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_NodalBankMMClaimed_ApplID' AND object_id = OBJECT_ID('dbo.t_NodalBankMMClaimed'))
CREATE NONCLUSTERED INDEX IX_NodalBankMMClaimed_ApplID
ON dbo.t_NodalBankMMClaimed (ApplID)
INCLUDE (IsMMProcessed);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SecNodalBankMMClaimed_SecLoanApplID' AND object_id = OBJECT_ID('dbo.t_SecNodalBankMMClaimed'))
CREATE NONCLUSTERED INDEX IX_SecNodalBankMMClaimed_SecLoanApplID
ON dbo.t_SecNodalBankMMClaimed (SecLoanApplID)
INCLUDE (IsMMProcessed);
GO

/* -------------------------------------------------------------------------------------
   m_AgencyOffDetail  (#AgencyZoneMap build)
   ------------------------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_AgencyOffDetail_AgencyOffID_StateId' AND object_id = OBJECT_ID('dbo.m_AgencyOffDetail'))
CREATE NONCLUSTERED INDEX IX_AgencyOffDetail_AgencyOffID_StateId
ON dbo.m_AgencyOffDetail (AgencyOffID, AgencyOffStateId);
GO
