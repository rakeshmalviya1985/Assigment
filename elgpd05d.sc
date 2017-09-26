/* HP Port 11517 20100115 - From pendingcheckin/temp/elgpd05d.sc 1.258 */
#pragma ident "@(#)elgpd05d.sc	1.281 10/31/13 EDS"
/************************************************************************/
/*                                                                      */
/*  Program Name:   ELGPD05D                                            */
/*                                                                      */
/*  Description:    KAECSES UPDATE PROCESS - update the MMIS with       */
/*                  beneficiary's eligibility information.              */
/*                                                                      */
/*  Input Parms:    Optional -m for monthly run                         */
/*                                                                      */
/*  Input:          eldj005.dat - Sorted KAECSES eligibility file       */
/*                  T_RE_NEG_ACT_DATE                                   */
/*                  T_RE_CITIZEN_DSC                                    */
/*                  T_COUNTY_OFFICE                                     */
/*                  T_CDE_AID                                           */
/*                  T_PUB_HLTH_PGM                                      */
/*                  T_CLM_PATLIAB_X                                     */
/*                                                                      */
/*  Input/Output:   T_SYSTEM_PARMS                                      */
/*                  T_SYSTEM_KEYS                                       */
/*                  T_RE_TXN_SUM                                        */
/*                  T_RE_BASE                                           */
/*                  T_RE_RACE_XREF                                      */
/*                  T_RE_ELIG                                           */
/*                  T_RE_AID_ELIG                                       */
/*                  T_RE_NAME_XREF                                      */
/*                  T_RE_ADDRESS                                        */
/*                  T_RE_SPEND_LIAB                                     */
/*                  T_RE_SPEND_MET                                      */
/*                  T_RE_CASE                                           */
/*                  T_RE_CASE_XREF                                      */
/*                  T_RE_CASE_WORKER_XREF                               */
/*                  T_RE_PREMIUM                                        */
/*                  T_RE_MS_INCOME                                      */
/*                  T_RE_POV_PRCNT                                      */
/*                  T_RE_TRANSPORT_LEVEL                                */
/*                                                                      */
/*  Output:         T_RE_EPSDT_LETTER                                   */
/*                  T_RE_EPS_ABNORMAL                                   */
/*                  T_RE_ERR                                            */
/*                                                                      */
/*  Link procedures: None                                               */
/*                                                                      */
/*  Special Logic Notes: None                                           */
/************************************************************************/
/*                                                                      */
/*                       MODIFICATION LOG                               */
/*                                                                      */
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO6262   04/20/2004 James Mercer     During monthly run, only call  */
/*                                       MC if next future month        */
/*  CO6376   04/21/2004 Lisa Salgat      Modified getLatestEligMonth to */
/*                                       bypass ADAP record types.      */
/*  CO6280   04/26/2004 Lisa Salgat      Added update to t_system_parms */
/*                                       for KCSESPRM program name.     */
/*  CO6699   06/08/2004 Lisa Salgat      Updated AIDCSR to select only  */
/*                                       non-historied rows.  Added max */
/*                                       array size to abend if greater */
/*                                       than max.                      */
/*  CO6611   06/10/2004 Lisa Salgat      Added logic to process SOBRA   */
/*  CO6576                               spenddown and spenddown when   */
/*                                       elig overlap occurs.           */
/*  CO6811   06/24/2004 Lisa Salgat      Updated select to not include  */
/*                                       eff dates in selectCaseInfo.   */
/*  CO7071   07/27/2004 Lisa Salgat      Updated spenddown logic to     */
/*                                       error when met and increased   */
/*                                       and new end date would equal.  */
/*  CO6930   07/28/2004 Lisa Salgat      Created insertTransportLevel   */
/*                                       procedure.                     */
/*  CO6816   08/17/2004 Lisa Salgat      Added sak_case to t_re_aid_elig*/
/*  CO6930   11/04/2004 Lisa Salgat      Added insert to transport lvl  */
/*                                       when gap in eligibility.       */
/*  CO7534   11/22/2004 Lisa Salgat      Added spenddown overlap check  */
/*  CO6816   12/10/2004 Lisa Salgat      Added new check of t_re_case   */
/*                                       when num_case not same as file.*/
/*  CO7534   01/18/2005 Lisa Salgat      Updated spenddown overlap check*/
/*                                       when start date is the same but*/
/*                                       different case number.         */
/*  CO7534   02/04/2005 Lisa Salgat      Updated spenddown overlap case */
/*                                       SQL, replaced ANDs with ORs    */
/*                                       different case number.         */
/*  CO7889   02/24/2005 Lisa Salgat      Added processBase function to  */
/*                                       processCase funtion when new   */
/*                                       case is added to case_xref.    */
/*  CO7534   03/28/2005 Lisa Salgat      Updated spenddown overlap case */
/*                                       SQL and moved spenddown flag   */
/*                                       check before inserts and update*/
/*                                       to t_re_elig to not insert if  */
/*                                       spenddown overlap and valid    */
/*                                       spenddown type.                */
/*  CO8099   04/28/2005 Lisa Salgat      Spenddown met check checking if*/
/*                                       row exists on met table.  Also */
/*                                       updated claims check to use sum*/
/*                                       instead of cursor.             */
/*  CO8306   07/08/2005 Lisa Salgat      Rewrote processCase procedure  */
/*                                       to handle retroactive case     */
/*                                       numbers.                       */
/*  CO8306   08/15/2005 Lisa Salgat      Updated to fix issues with case*/
/*                                       updates                        */
/*  CO8511   09/16/2005 Lisa Salgat      Added process to update benes	*/
/*                                       for Hurricane Katrina.  New    */
/*                                       functions are documented for   */
/*                                       ease if removal needed.        */
/*                                       Correct CSR# in new procedures */
/*  CO8306   09/29/2005 Lisa Salgat      Fixed issue with spenddwn being*/
/*                                       updated when it shouldn't be & */
/*                                       fixed case update to only do   */
/*                                       when active eligibility.       */
/*  CO7534   10/10/2005 Lisa Salgat      Updated overlap SQL to compare */
/*                                       dates to case_xref dates too.  */
/*  CO8509   10/10/2005 Lisa Salgat      Added countable income field   */
/*                                       processing (t_re_ms_income)    */
/*  CO8804   01/18/2006 Lisa Salgat      Updated to process spenddown   */
/*                                       for equal elig only when case  */
/*                                       number is the same.            */
/* CO8831    02/28/2006 Srinivas D       Added presumptive disability   */
/*                                       indicator logic.               */
/* CO9489    05/26/2006 Srinivas D       Modified for presumptive elig  */
/*                                       to include additional benefit  */
/*                                       plan rules                     */ 
/* CO9195    08/10/2006 Tomas G          Commented out calls to function*/
/*                                       processKatrina(...).           */
/* CO10316   09/12/2007 Dale Gallaway    When a prior elig arrives with */
/*                                       a different pop code, do not   */ 
/*                                       allow it to override the curr. */
/*                                       elig.     Find "co10316".      */ 
/* CO11182   05/14/2008 Dale Gallaway    Modified the code to call mgd  */
/*                                       function when an eligibility   */
/*                                       record is errored off with an  */
/*                                       issue in spenddown base on the */
/*                                       incoming rec by adding a flag  */
/*                                       end_flag_set_from_processSpndwn*/
/* CO11243   06/26/2008 Srini Donepudi   Modified insertIDCard() to add */
/*                                       CDE_ID_CARD_RSN to INSERT SQL  */
/* CO11212   12/03/2008 Dale Gallaway    Modified processCaseHist() to  */
/*                                       reset h_case_dte_end so        */
/*                                       t_re_case_xref.dte_end will be */
/*                                       set back to the date before    */
/*                                       the original benefit month.    */
/* CO11849   04/05/2009 Srini Donepudi   Modified to capture the new    */
/*                                       fields case ssn, case dob, resp*/
/*                                       address and store in t_re_case,*/
/*                                       t_re_resp_person.              */
/* CO11847   04/15/2009 Dale Gallaway    Modified to capture the        */
/*                                       premium billing information.   */
/* CO11847   06/01/2009 Dale Gallaway    Modified to error off when     */
/*                                       receiving an SQL error in      */
/*                                       premium billing routines.      */
/* CO11847   06/08/2009 Dale Gallaway    Modified to ...                */
/*                                       1) allow HW and WH to co-exist */
/*                                       for premium billing.           */
/*                                       2) prem_bill_yyyymm was not    */
/*                                       initialized.                   */
/* CO11847   06/26/2009 Dale Gallaway    Premium Billing modifications: */
/*                                       1) HealthWave is shut off.     */
/*                                          Do a find on "HealthWave".  */
/*                                       2) Case number change logic    */
/*                                       3) On lower elig error, prem   */
/*                                          updates should occur.       */
/*                                       4) Update the Last update date.*/
/* CO11847   07/07/2009 Dale Gallaway    Premium Billing, fix the CASE  */
/*                                       number change logic.           */
/* CO12276   10/21/2009 Hari Singam      Turn on the HealthWave for     */  
/*                                       Premium billing.               */
/* CO12276   11/16/2009 Dale Gallaway    Fix the PB&C logic for HW retro*/  
/*                                       in processPremBill().          */
/*                                       Test for bAddedRecord & updat_aid_flag. */ 
/* CO12276   11/24/2009 Dale Gallaway    Fix the PB&C logic for HW retro*/  
/*                                       in processPremBill(), again.   */
/*                                       Create premBillExist ().       */ 
/* CO12276   12/01/2009 Dale Gallaway    Fix the PB&C logic for HW retro*/  
/*                                       in processPremBill(), again.   */
/*                                       Create prelimPremBill ().      */ 
/* CO12276   12/17/2009 Dale Gallaway    Fix the PB&C logic for HW retro*/  
/*                                       in processPremBill(), again.   */
/*                                       Remove prelimPremBill ().      */ 
/* CO12276   12/21/2009 Dale Gallaway    Fix the PB&C logic for HW retro*/  
/*                                       #5!                            */
/*                                       Create premBillCovT21 ().      */ 
/* CO12276   01/05/2010 Dale Gallaway    Initialize replace_aid_index   */
/*                                       and extend_aid_index.          */
/* CO11961   01/06/2010 Hari Singam      Stop creating new NEMT Levels  */
/*                                       in table t_re_transport_level. */
/* 11517     01/20/2010 Titus Johnson    Added calculateLatLong         */
/*                                                                      */
/* CO11961   02/11/2010 Hari Singam      Commenting out function call   */
/*                                       validateTransportLevel         */
/*                                                                      */
/* CO12276   03/23/2010 Dale Gallaway    Needed a 2nd call to           */
/*                                       premBillExist(case, HW);       */
/*                                                                      */
/* CO12846   07/30/2010 Dale Gallaway    "Payee name" or med_surname,   */
/*                                       med_given_name; change the     */
/*                                       logic so if first or last name */
/*                                       is nonblank, use it.           */
/*                                                                      */
/* CO12846   08/17/2010 Dale Gallaway    Fix the test for "AS" and "FC" */
/*                                       benes using memcmp.            */
/*                                                                      */
/* CO11869   03/08/2011 Dale Gallaway    Fix the T21 retro problem when */
/*                                       needing to update the          */
/*                                       day-specific enrollment.       */
/*                                       Find 'CO11869'.                */
/*                                                                      */
/* CO11869   05/05/2011 Dale Gallaway    Fix the T21 retro problem,     */
/*                                       after month-end ran.           */
/*                                       Find 'CO11869b'.               */
/* CO11869   05/17/2011 Dale Gallaway    Fix the T21 retro problem,     */
/*                                       after month-end ran,           */
/*                                       set replace_aid_flag  = TRUE;  */
/*                                       Find 'CO11869c'.               */
/* CO14275   06/08/2012 Dale Gallaway    New benefit plan 'INMAT'.      */
/*                                       1) change checkValidSpend.     */
/*                                       Back out CO11869b and CO11869c */
/*                                       changes because they never     */
/*                                       made it to prod.               */
/* CO14275   07/12/2012 Dale Gallaway    Add logic in checkValidSpend   */
/*                                       for INMAT, new errs 2170,2171. */
/* CO14275   07/23/2012 Dale Gallaway    Field desc_field is [15+1],    */
/*                                       doing a memcpy > 15 chars      */
/*                                       causes the ORA-01480 error.    */
/* CO14286   08/03/2012 Madhuri D        Add new field - dte_application*/
/* CO14494   08/30/2012 Dale Gallaway    Add new field -                */
/*                                       ind_addr_invalid to t_re_base. */
/* CO14538   11/05/2012 Madhuri D        creating NEMT levels for FFS   */
/* CO14538   11/27/2012 Madhuri D        NEMT created for all Benes     */
/* CO14538   12/05/2012 Dale Gallaway    Fix a transport SQL at         */
/*                                       'CO15538 12/05'.               */
/* CO14709   12/31/2012 Dale Gallaway    Do not call main_mgd_care_kaecses */
/*                                       if the elig record errored off.   */
/*                                       Created sv_error.                 */
/* CO14709   12/31/2012 Dale Gallaway    Call main_mgd_care_kaecses     */
/*                                       if on dupe records,one is good.*/
/* CO14736   08/18/2013 Nirmal T         Added verifyCaseWorkerInfo and */
/*                                       insertCaseWorkerInfo functions.*/
/* CO14736   10/07/2013 Nirmal T         Modified insertCaseWorkerInfo  */
/*                                       update sql's column name.      */
/*                                                                      */
/************************************************************************/

/* Header files */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <tools.h>
#include "elbphier.h"
#include "recip_kaecses_elig.h"
#include "mgd_kaecses.h"
#include "db_oci.h"
#include "elgpd910.h" 

exec sql include sqlca;

#define CALL_MGD_CARE TRUE
#define MAX_AID_ARRAY 200
#define MAX_ELIG_ARRAY 20
#define DEFAULT_START_DATE 19000101
#define HLTH_MN     3
#define HLTH_SOBRA  5
#define HLTH_INMAT 79

#define sfree(p) if(p!=NULL) {free(p); p=NULL;}
/************************************************************************/
/*  Define global variables                                             */
/************************************************************************/
#define OPEN_DATE   22991231
#define DAILY       'D'
#define MONTHLY     'M'
#define CURR_CASE_XREF "C"
#define HIST_CASE_XREF "H"

/************************************************************************/
int readcnt = 0;
int sv_error = 0;
int case_index = 0;
int end_process = FALSE;
int bActiveElig = FALSE;
int bActiveEligMonth = FALSE;
int bEqualElig = FALSE;
int bLowerElig = FALSE;
int bMidMonthElig  = FALSE;
int bValidSpendRec = FALSE;
int bAddedRecord   = FALSE;
int bCaseVerified  = FALSE;
int bFirstTime     = TRUE;
int id_card_printed = FALSE;
int idayofmon = 0;
int is_future_month = FALSE;
int override_bp_hier = FALSE;
int end_flag_set_from_processSpndwn = FALSE;
int updat_aid_flag = FALSE;

/* Premium Billing */ 
char *sv_filename = NULL; 
int prem_bill_ctr = 0; 
char char5           [6]; 
char is_hw        = 'n'; 
char is_wh        = 'n'; 
char is_partial_month = 'n';
char prem_case_change = 'n'; 
char prem_case_num_prev [10+1]; 
char was_hw       = 'n'; 
char was_wh       = 'n'; 
char sv_ben_mon      [7]; 
char sv_sk_recip    [10]; 
int open_date = OPEN_DATE;
FILE *prembillout = NULL; 

PREM_BILL_ERR_EXT  *prem_bill_rec; 
 
exec sql begin declare section;
int sv_sak_premium1 = 0; 
int sv_sak_premium2 = 0; 
int sv_sak_premium3 = 0; 
int sv_prem_eff3 = 0; 
int sv_prem_end3 = 0; 
int sv_prem_end5 = 0; 
double sv_prem_amt3 = 0;
int prem_eff_date = 0;
int prem_end_date = 0;
int prem_sak_case = 0; 
int prem_bill_months; 
int prem_bill_yyyymm; 
int prelim_sk_case = 0;

int num_accept = 0;
int num_reject = 0;

int p19_num_rank = 0;
int p21_num_rank = 0;
int p19_sak_pub_hlth;
int p21_sak_pub_hlth;
  
/* Hurricane Katrina beneficiary changes */
int sql_sak_mc_entity ;
int sql_HW19_cond_code;
int sql_HCK_cond_code;
int ktr_pub_hlth;
/* end Hurricane Katrina beneficiary changes */

/*restart variables*/
int qty_txn_count;
int qty_commit_freq;
int next_commit = 0;

/*New saks*/
int new_sak_recip,
     new_sak_case,
     new_sak_case_xref,
     new_sak_link,
     new_sak_aid_elig,
     new_sak_pgm_elig,
     new_sak_pov_prcnt,
     new_sak_spend_liab,
     new_sak_rec_id_card,
     new_sak_premium, 
     new_sak_alert;

int bene_sak_case;
double ksc_spend_amt;
int base_end_ym;
int insert_case_dte_eff;
char insert_case_status[1+1];
int insert_case_dte_end;
int base_st_ym;
int neg_action_dte;
int spn_sak_short;
int spn_sak_spend_liab;
int spn_sak_case;
int spn_dte_effective;
int spn_dte_effective_yyyymm;
int spn_dte_end;
int spn_dte_end_yyyymm;
double spn_amt_spenddown;
int sak_pub_hlth[5];
int hold_sak_pub_hlth;
int num_rank[5];
int ksc_num_rank      = 0;
int ksc_2nd_num_rank  = 0;
int bene_num_rank     = 0;
char name_field[15+1];
char desc_field[15+1];
char base_adr_street1[25+1];
char base_adr_street2[25+1];
char base_adr_city[15+1];
char base_adr_state[2+1];
char base_adr_zip_code[5+1];
char base_adr_zip_code_4[4+1];
double base_lat;
double base_long;
int base_geo_qual;
char base_num_ssn[9+1];
int base_dte_birth;
char base_cde_sex[1+1];
char base_cde_ethnic[1+1];
char base_cde_county[2+1];
char base_cde_office[1+1]="1";
int base_sak_cnty_off_srv;
char base_num_phone[10+1];
int base_sak_cde_phone;
int base_sak_cde_citizen;
char base_ind_spec_hlth[1+1]=" ";
char base_cde_soundex[4+1];
char base_cde_lang_written[2+2];
char base_cde_lang_spoken[2+2];
char base_cde_comm_media[2+1];
char base_resp_pty_nam_last[17+1];
char base_resp_pty_nam_first[12+1];
char base_resp_pty_nam_mid[1+1]=" ";
int base_dte_cred_covg_cert = 0;
int base_dte_continuous_elg;
int base_dte_application;
int cont_elig;
int case_dte_cert;
int case_dte_end;
int case_dte_birth;
char case_num_ssn[9+1];
char resp_adr_street1[25+1];
char resp_adr_street2[25+1];
char resp_adr_city[15+1];
char resp_adr_state[2+1];
char resp_adr_zip_code[5+1];
char resp_adr_zip_code_4[4+1];

int orig_benefit_month;
int orig_benefit_month_lastday;
int orig_benefit_month_lastday_pls1;
int orig_benefit_month_min1day;
int benefit_month_min1day;
int benefit_month_yyyymm;
int benefit_month_yyyymmdd;
int benefit_month_lastday;
int benefit_month_lastday_pls1;
int id_card_benefit_start;
int id_card_benefit_end;

int sak_case;
int benefit_case;
int sak_recip;
int prev_sak_recip;
int elig_sak_recip;
int sak_cde_aid;
int prev_sak_cde_aid = 0;
int curr_priority_code = 0;
int curr_lastday;
int curr_dte;
int curr_dte_lstdy_pls1mth;
int curr_dte_1stday;
int curr_dte_yyyymm;
int curr_dte_pls1mth_yyyymm;
int curr_dte_min1day;
int curr_dte_pls1day;
int prem_hw_amt_t21 = 0;
int prem_wh_amt = 0;
double msIncome = 0;
int epsdt_age;
int review_dte;
int review_dte_yyyymmdd;
int percent;
double net_income;
int priority_code;

char char_curr_dte[8+1];
char char_dte_birth[8+1];
char cde_prem_type[2+1];
char pop_code[2+1];
char retro_t21[1+1];
int dte_death;
char prev_adr_street1[30+1];
char prev_adr_street2[30+1];
char prev_adr_city[18+1];
char prev_adr_state[2+1];
char prev_adr_zip_code[5+1];
char prev_adr_zip_code_4[4+1];
char prev_nam_first[12+1];
char prev_nam_last[17+1];
char prev_nam_mid[1+1]=" ";
char prev_cde_county[2+1];
char prev_cde_office[1+1];

char man_code[1+1];
char mid_time[14+1];
char time_stamp[8+1];
char dte_time_stamp[14+1];
char cap_ind[1+1];
char race_code[10+1];
char med_nam_first[12+1];
char med_nam_last[17+1];
char med_nam_mid[1+1]=" ";
char case_nam_first[12+1];
char case_nam_last[17+1];
char case_nam_mid[1+1]=" ";
char nam_first[12+1];
char nam_last[17+1];
char nam_mid[1+1]=" ";
char rp_nam_first[12+1];
char rp_nam_last[17+1];
char rp_nam_mid[1+1]=" ";
char legal_status[2+1];
char citizen[2+1];
char cash_prog[2+1];
char cash_prog_sb[2+1];
char src_fund[2+1];
char med_elig_ind[2+1];
char pres_dis_ind[2+1];
char prog_type[2+1];
char prog_subtype[2+1];
char cov_type[2+1] = "1";
char title[2+1] = "2";
char qmb_ind[1+1] = "Y";
char part_code[2+1];
char cde_lime[1+1];
char runParm;

char char_dte_application[8+1]; 
char id_medicaid[11+1];
char case_number[8+1];
char case_cde_status[1+1];
int dte_cert;
char ksc_case_number[10+1];
char client_number[10+1];
char base_id[11+1];
char med_subtype[2+1];
char prog_type[2+1];
char relation_code[2+1];
char worker[6+1];
char placement[2+1];

int parm_dte;
int parm_dte_pls1;
int aid_elig_start[3];
int aid_elig_end[3];
int aid_elig_case[3];

int aid_sak_aid_elig[MAX_AID_ARRAY];
int aid_sak_cde_aid[MAX_AID_ARRAY];
int aid_sak_case[MAX_AID_ARRAY];
int aid_dte_eff[MAX_AID_ARRAY];
int aid_dte_end[MAX_AID_ARRAY];
int aid_dte_created[MAX_AID_ARRAY];
int aid_dte_last_updated[MAX_AID_ARRAY];
int aid_dte_active_thru[MAX_AID_ARRAY];
char aid_cde_status1[MAX_AID_ARRAY][1+1];
char aid_kes_cde_med_type[MAX_AID_ARRAY][2+1];
char aid_kes_cde_med_sub[MAX_AID_ARRAY][2+1];
char aid_kes_cde_inv_med_sb[MAX_AID_ARRAY][2+1];
char aid_kes_cde_med_elig[MAX_AID_ARRAY][2+1];
char aid_kes_cde_cash_type[MAX_AID_ARRAY][2+1];
char aid_kes_cde_cash_sub[MAX_AID_ARRAY][2+1];
char aid_kes_cde_fund_src[MAX_AID_ARRAY][2+1];
char aid_kes_cde_typ_place[MAX_AID_ARRAY][2+1];
char aid_kes_cde_legal_stat[MAX_AID_ARRAY][2+1];
char aid_kes_ind_qmb[MAX_AID_ARRAY][1+1];
char aid_kes_cde_partic[MAX_AID_ARRAY][2+1];
char aid_kes_cde_lime[MAX_AID_ARRAY][2+1];
char aid_kes_cde_lime_title[MAX_AID_ARRAY][2+1];
char aid_kes_cde_pdi[MAX_AID_ARRAY][2+1];

int elig_sak_pgm_elig[MAX_ELIG_ARRAY];
int elig_sak_pub_hlth[MAX_ELIG_ARRAY];
int elig_dte_eff[MAX_ELIG_ARRAY];
int elig_dte_end[MAX_ELIG_ARRAY];
char elig_cde_status1[MAX_ELIG_ARRAY][1+1];
int elig_num_rank[MAX_ELIG_ARRAY];
char elig_covg_cert[MAX_ELIG_ARRAY][1+1];
int elig_sak_aid_elig[MAX_ELIG_ARRAY];
int  elig_sak_cde_aid[MAX_ELIG_ARRAY];
int  elig_sak_case[MAX_ELIG_ARRAY];
char elig_pop_code[MAX_ELIG_ARRAY][2+1];
char elig_legal_status[MAX_ELIG_ARRAY][2+1];
char elig_cash_prog[MAX_ELIG_ARRAY][2+1];
char elig_cash_prog_sb[MAX_ELIG_ARRAY][2+1];
char elig_src_fund[MAX_ELIG_ARRAY][2+1];
char elig_typ_place[MAX_ELIG_ARRAY][2+1];
char elig_med_elig_ind[MAX_ELIG_ARRAY][2+1];
char elig_med_subtype[MAX_ELIG_ARRAY][2+1];
char elig_prog_type[MAX_ELIG_ARRAY][2+1];
char elig_prog_subtype[MAX_ELIG_ARRAY][2+1];
char elig_qmb_ind[MAX_ELIG_ARRAY][1+1];
char elig_partic[MAX_ELIG_ARRAY][2+1];
char elig_citizen[MAX_ELIG_ARRAY][2+1];
char elig_pres_dis_ind[MAX_ELIG_ARRAY][2+1];
int elig_epsdt_age[MAX_ELIG_ARRAY];
int aid_elig_dte_eff[MAX_ELIG_ARRAY];
int aid_elig_dte_end[MAX_ELIG_ARRAY];


ELIG_BENEFIT_PLAN_S elig_bp[MAX_BP];
ELIG_BENEFIT_PLAN_S * elig_bp_ptr;

exec sql end declare section;

char base_spaces[11+1]= "           ";
/************************************************************************/
/*  Define record structures                                            */
/************************************************************************/

ELIG_REC  *eligrec = NULL;
ELIG_REC  *headptr = NULL;
ELIG_REC  *tempptr = NULL;
ELIG_REC  *currptr = NULL;

/************************************************************************/
/*  Forward Declarations                                                */
/************************************************************************/

/*Internal Function Calls*/

extern int  ConnectDB          (void);
static int  allocMemory        (void);
static int  allocMemory        (void);
static int  ben_mth_b1         (void);
static int  ben_mth_copy       (void);
static int  ben_mth_extend_flg_true (void);
static int  ben_mth_insert     (void);
static int  ben_mth_new_elig   (void);
static int  ben_mth_update     (void);
static int  benefitMth         (void);
static int  benefitPlan        (void);
static int  calBenefitMth      (void);
static int  checkBaseId        (void);
static int  checkCanCoexist    (int *, int, int);
static int  checkIfAidsDiffer  (int *, int);
static int  checkMultiPlan     (int *,int *,int,char [],char [],char [],char [],char [],char [],char [],char [],char [],char [],char [],int,char []);
static int  checkPremBill      (void);
static int  checkProcessSpndwn (void);
static int  checkSpndwn        (int *);
static int  checkValidSpend    (void);
static int  claimsCheck        (int *);
static int  commet_it          (void);
static int  compareAidEligCase (int);
static int  copyAidElig        (int);
static int  copyAidEligSeg     (int);
static int  copyAidEligs       (int);
static void copyBftFields      (void);
static int  copyElig           (int, int, int);
static int  copyExtendAidEligs (int, int);
static int  createTmpPeRevTxn  (void);
static int  declareAidElig     (int);
static int  declareElig        (void);
static int  deleteCaseXref     (void);
extern int  double_compare     (double, double);
static int  extendElig         (int);
static int  fetchAidElig       (void);
static int  fetchElig          (void);
static int  getLatestEligMonth (int *);
static int  getNewSakAidElig   (void);
static int  getNewSakCase      (void);
static int  getNewSakCaseXref  (void);
static int  getNewSakLink      (void);
static int  getNewSakPovPrcnt  (void);
static int  getNewSakPremium   (void);
static int  getNewSakRecIdCard (void);
static int  getNewSakRecip     (void);
static int  getNewSakSpendLiab (void);
static int  getSakPopCode      (void);
static int  hierarchy          (int cnt);
static int  historyAidElig     (int);
static int  historyElig        (void);
static int  histPremLogic      (int, int, int, double, double, int);
static int  init_main          (void);
static int  inputCount         (void);
static int  insertAidEligSeg   (int);
static int  insertBase         (void);
static int  insertCase         (char[], char[], char[]);
static int  insertCaseXref     (int, int, char[]);
static int  insertCountableIncome(int, int, double, char[]);
static int  insertElig         (int);
static int  insertEligSeg      (int, int);
static int  insertErrTbl       (int);
static int  insertIDCard       (int);
static int  insertNewBene      (void);
static int  insertOneElig      (int);
static int  insertPeRevTxn     (char[], int, int, int, char[], char[]); 
static int  insertPovPrcnt     (int);
static int  insertPreBillBene  (int); 
static int  insertPrem         (int, int, int, double, char);
static int  insertRace         (void);
static int  insertRespAddress  (void);
static int  insertSpndHst      (int, double);
static int  insertSpndwn       (int, int, double);
static int  insertTmpPeRevTxn  (char[], int, int, int, char[], char[]);
static int  insertTransportLevel(int, int, int);
static int  isrtPremLogic      (int, double); 
static int  lookForPremBill    (int);
static int  popCode            (void); 
static int  povPercent         (void);
static int  prelimPremBill     (void);
static int  premBillCovT21     (int);
static int  premBillErrRpt     (void);
static double premBillExist    (int, char []);  
static int  previousInfo       (void);
static int  processBase        (void);
static int  processCase        (void);
static int  processCaseHist    (int *);
static int  processElig        (FILE *);
static int  processMCSpecCon   (int, char []); 
static int  processPremBill    (void);
static int  processSpndwn      (void);
static int  processTmpPeRevTxn (void);
static int  processXrefId      (void);
static int  replaceAidElig     (int, int);
static int  respAddress        (void);
static int  retrieveSak        (void);
static int  retrieveSak        (void);
static int  selectAidEligSeg   (int *, int *, int);
static int  selectCaseInfo     (void);
static int  selectSpndwn       (void);
static int  slotPremBill       (void); 
static int  spndwnMet          (int, int);
static int  spndwnUnmet        (int);
static int  spndwnValidate     (int, int);
static int  updateAddressXref  (void);
static int  updateAidElig      (int, int, int, int, int);
static int  updateAidEligSeg   (int *, int, char[], int, int, int);
static int  updateBase         (void);
static int  updateCaseXref     (int, int, char[]);
static int  updateCountableIncome (void);
static int  updateCountyXref   (void);
static int  updateDemoGraphics (void);
static int  updateElig         (int, int, int);
static int  updateEligSeg      (int, int, int, int, int, int, int);
static int  updateEpsdtLet     (void);
static int  updateNameXref     (void);
static int  updatePovPrcnt     (int,int);
static int  updatePrem         (int, double);
static int  updateRaceXref     (void);
static int  updateRespAddress  (int,int);
static int  updateRestart      (void);
static int  updateSpendLiab    (int, int, double, int, int);
static int  update_t_patm      (void);
static int  update_t_patm      (void);
static int  verifyCaseInfo     (char [], char [], char []);
static int  verifyCaseNum      (void);
static int  verifyCaseXref     (void);
static int  validateTransportLevel(void);
static int  verifyCaseWorkerInfo(void);
static int  insertCaseWorkerInfo(int);

/*          calculateLatLong -- mapInfoSql   */
extern int  calculateLatLong (char [], char [], char [], char [], char [], 
                              double *, double *, int *); 

/*          determine_benefit_plan -- elbphier.sc  */
extern int  determine_benefit_plan (char [], char [], char [], char [], char [], char [], char [], 
                                    char [], char [], char [], char [], char [], char [],
                                    ELIG_BENEFIT_PLAN_S *); 
 
/*          getPopCodes <- libelig.so <- elgpd05g.c */
extern int  getPopCodes (char (*)[3] , 
                int *priority_code,
                char (*)[2],
                char (*)[2],
                char legal_status[],
                char citizenship[],
                char cash_prog[],
                char cash_prog_subtype[],
                char src_funding[],
                char med_elig_ind[],
                char ind_med_subtype[],
                char prog_type[],
                char prog_subtype[],
                char qmb_ind[],
                int age,
                char pres_dis_ind[]);

/************************************************************************/
/*                                                                      */
/*  Function Name:   main ()                                            */
/*                                                                      */
/*  Description:     Perform the appropriate initialization routines,   */
/*                   assign file pointers, open input files, close input*/
/*                   files, connects to the database, and call the main */
/*                   reporting function.                                */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
int main (int argc, char *argv[])
{

FILE *eligFd;
char *eligFile;

    switch (argc)
    {
       case 1:
         runParm = DAILY;
         break;
       case 2:
         if (strcasecmp("-m", argv[1])==0)
            runParm = MONTHLY;
         else
         {
            fprintf (stderr, "ERROR: Invalid monthly input parm\n");
            fprintf (stderr, "Correct Usage: PROGNAME (-m) \n   Where -m is monthly, no parameter is daily\n");
            return EXIT_FAILURE;
         }
         break;
       default:
         fprintf (stderr, "ERROR: Invalid number of parms\n");
         fprintf (stderr, "Correct Usage: PROGNAME (-m) \n   Where -m is monthly, no parameter is daily\n");
         return EXIT_FAILURE;
    }

	/* Get the ELIG file names and open them */
	eligFile = getenv ("dd_ELIG") ? getenv("dd_ELIG") : "dd_ELIG";

	if ((eligFd = fopen (eligFile, "rb")) == NULL)
        {
           fprintf (stderr, "ERROR: could not open ELIG file\n");
	   perror (eligFile);
	   return EXIT_FAILURE;
	}

	/* Connect to the database */
	if (ConnectDB() != 0)
	{
	   fprintf (stderr, "ERROR: could not connect to database\n");
	   return EXIT_FAILURE;
	}

	/* Initialize              */
      if (init_main() != 0)
	{
	   fprintf (stderr, "ERROR: Failure in init_main().      \n");
	   return EXIT_FAILURE;
	}

        /*Calls allocated memory routine*/
        if (allocMemory() != 0)
	{
	   fprintf (stderr, "ERROR: bad return from allocMemory\n");
	   return EXIT_FAILURE;
	}

        /* Retreives the saks needed to update the MMIS*/
	if (retrieveSak() != 0)
	{
	   fprintf (stderr, "ERROR: bad return from retrieveSak\n");
	   return EXIT_FAILURE;
	}

        /* create t_re_tmp_pe_rev_txn table to hold PE records that errored off due to PE benefit plan rules. 
           inorder to create a record in t_re_pe_rev_txn sak_recip is needed, sometimes incase of virgin elig 
           base record will be established after the PE record is errored off, this temp table will be used to 
           hold these records and actual records will be inserted into t_re_pe_rev_txn at the end of processing.
           qty_txn_count = 0 check is used to bypass the create temp table incase of restarts. 
        */ 
        if (qty_txn_count == 0)
        {
            createTmpPeRevTxn(); 
        }
 
	/* Calls the ELIG routine */
   /*(commits are called within processElig)*/
	if (processElig(eligFd) != 0)
	{
	   fprintf (stderr, "ERROR: bad return on processELIG\n");
	   return EXIT_FAILURE;
	}

        if (processTmpPeRevTxn() != 0)
        {
           fprintf (stderr, "ERROR: bad return on processTmpPeRevTxn\n");
           return EXIT_FAILURE;
        }

	/*Close ELIG input files */
	if (fclose (eligFd) != 0)
	{
	   fprintf (stderr, "ERROR: could not close the ELIG file\n");
	   perror (eligFile);
	   return EXIT_FAILURE;
	}

   if (runParm == MONTHLY)
   {
      if(update_t_patm() != 0 )
      {
         fprintf (stderr, "ERROR: Problem with update t_parm table \n");
         return EXIT_FAILURE;
      }

      /*Commit all modified rows*/
      if (commet_it() != 0)
      {
         fprintf (stderr, "ERROR: could not commit (1)\n");
         return EXIT_FAILURE;
      }
   }

	return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   init_main()                                        */
/*                                                                      */
/*  Description: Open the premium billing error file. (output)          */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11847  04/29/2009  Dale Gallaway   Initial Release                */
/************************************************************************/
static int init_main()
{
   char FNC[] = "elgpd05d (init_main):";

   if (runParm == DAILY)
   {                                             /* Premium Billing Error Rpt ELG-0010-D*/
      if ( (sv_filename = getenv("dd_ERROR_PREM")) == NULL) 
      {
         fprintf(stderr, "%s dd_ERROR_PREM environment variable not set\n", FNC);
         return EXIT_FAILURE;
      }
      else
      {
         prembillout = fopen(sv_filename,"wt");
         if (prembillout == NULL)
         {
            fprintf(stderr,"%s unable to open dd_ERROR_PREM output file\n", FNC);
            return EXIT_FAILURE;
         }
      }
   }
   if ( (prem_bill_rec = malloc(sizeof(PREM_BILL_ERR_EXT)) ) == NULL)
   {
       fprintf(stderr, "%s Unable to malloc %ld bytes for prem_bill_rec buffer\n",
               FNC, sizeof(PREM_BILL_ERR_EXT) );
       return EXIT_FAILURE;
   }

   EXEC SQL
   SELECT date_parm_1
     INTO :prem_bill_months 
     FROM t_system_parms
    WHERE nam_program = 'ELGPREM1';

   if (sqlca.sqlcode != 0)
   {
       fprintf (stderr, "ERROR: could not select Prem Bill retro date. \n");
       fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
       return EXIT_FAILURE;
   }
   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   commitElig()                                       */
/*                                                                      */
/*  Description: Commits and records results of next group of elig recs.*/
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   11/04/2003 James Mercer     Initial Release                */
/************************************************************************/
static int commitElig()
{
   /*Update the summary table (T_RE_TXN_SUM) with the amount of records modified on the MMIS*/
   if (inputCount() != 0)
   {
      fprintf (stderr, "ERROR: bad return from inputCount\n");
      return EXIT_FAILURE;
   }

   /*update restart count*/
   if(updateRestart() != 0 )
   {
      fprintf (stderr, "ERROR: Problem with update t_batch_restart table \n");
      return EXIT_FAILURE;
   }

   /*Commit all modified rows*/
   if (commet_it() != 0)
   {
      fprintf (stderr, "ERROR: could not commit (2)\n");
      return EXIT_FAILURE;
   }

   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateRestart()                                    */
/*                                                                      */
/*  Description: Called to update the number of records processed this  */
/*               run.  (Will be reset to 0 after end of run.)           */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   11/04/2003 James Mercer     Initial Release                */
/************************************************************************/
static int  updateRestart()
{

   EXEC SQL
      UPDATE  t_batch_restart
         SET  qty_txn_count = :qty_txn_count
       WHERE  nam_program  = 'ELGPD05D';

   if (sqlca.sqlcode != 0)
   {
      fprintf (stderr, "ERROR: could not update t_batch_restart\n");
      return EXIT_FAILURE;
   }

   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   retrieveSak()                                      */
/*                                                                      */
/*  Description: Pull the last saks used from t_system_keys in order to */
/*               insert new segments into the MMIS.                     */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO9489   05/26/2006 Srinivas D       Modified to get p19 & p21 saks */
/************************************************************************/
static int retrieveSak()
{

     /* Pulls the current cycle date*/
     sqlca.sqlcode = 0;
     EXEC SQL
        SELECT dte_parm_2,
               to_number(to_char(to_date(dte_parm_2, 'YYYYMMDD')+1, 'YYYYMMDD'))
        INTO   :parm_dte,
               :parm_dte_pls1
        FROM   t_system_parms
        WHERE nam_program = 'AIMCYCLE';

	if (sqlca.sqlcode != 0)
	{
           fprintf (stderr, "ERROR: could not parm date from t_system_parms\n");
	   return EXIT_FAILURE;
        }

       fprintf(stdout,"Parm date we're using: %d\n", parm_dte);

     /*Pulls the negative action date for spenddown.  If there is no negative action date, force the program to abend*/
     sqlca.sqlcode = 0;
     EXEC SQL
        SELECT dte_neg_action
        INTO   :neg_action_dte
        FROM  t_re_neg_act_date
        WHERE mth_process = substr(:parm_dte,0,6);

	if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
	{
           fprintf (stderr, "ERROR: could not select negative action date from t_re_neg_act_date\n");
	   return EXIT_FAILURE;
        }
        else if (sqlca.sqlcode == 100)
        {
           fprintf (stderr, "ERROR: NO NEGATIVE ACTION FOUND, CAN NOT CONTINUE WITHOUT A NEGATIVE ACTION DATE\n");
	   return EXIT_FAILURE;
        }

     /*Pulls the numbers for the accepted and rejected transactions and will increment the numbersas each transactionis rejected or accepted*/
     sqlca.sqlcode = 0;
       EXEC SQL
         SELECT num_accepted,
                num_rejected
         INTO :num_accept,
              :num_reject
         FROM t_re_txn_sum
         WHERE cde_txn_type = 'ELIG';

	if (sqlca.sqlcode != 0)
	{
           fprintf (stderr, "ERROR: could not select counts from t_re_txn_sum\n");
           fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
	   return EXIT_FAILURE;
        }
      prem_bill_months = prem_bill_months * -1; 

     /*Pulls the current date in differenct formats to use throughout the program*/
     sqlca.sqlcode = 0;
       EXEC SQL
       SELECT to_char(SYSDATE, 'YYYYMMDD'),
              to_number(to_char(SYSDATE, 'YYYYMMDD')),
              to_number(to_char(SYSDATE, 'YYYYMM'))||'01',
              to_number(to_char(SYSDATE, 'YYYYMM')),
              to_char(to_date(SYSDATE)-1, 'YYYYMMDD'),
              to_char(to_date(SYSDATE)+1, 'YYYYMMDD'),
              to_char(last_day(add_months(to_date(SYSDATE),+1)), 'YYYYMMDD'),
              to_char(last_day(to_date(SYSDATE)), 'YYYYMMDD'),
              to_char(add_months(to_date(SYSDATE),+1), 'YYYYMM'),
              to_char(add_months(to_date(SYSDATE),+ :prem_bill_months), 'YYYYMM')
       INTO   :char_curr_dte,
              :curr_dte,
              :curr_dte_1stday,
              :curr_dte_yyyymm,
              :curr_dte_min1day,
              :curr_dte_pls1day,
              :curr_dte_lstdy_pls1mth,
              :curr_lastday,
              :curr_dte_pls1mth_yyyymm,
              :prem_bill_yyyymm
       FROM   dual;

     if (sqlca.sqlcode != 0)
     {
        fprintf(stderr,"ERROR: could not select current date\n");
        return EXIT_FAILURE;
     }

     /*Pulls restart numbers*/
     sqlca.sqlcode = 0;
       EXEC SQL
       SELECT qty_txn_count,
              qty_commit_freq
       INTO   :qty_txn_count,
              :qty_commit_freq
       FROM   t_batch_restart
       WHERE  nam_program = 'ELGPD05D';

     if (sqlca.sqlcode != 0)
     {
        fprintf(stderr,"ERROR: could not select from t_batch_restart\n");
        return EXIT_FAILURE;
     }

     sqlca.sqlcode = 0;
     EXEC SQL
     SELECT a.num_rank,
            b.sak_pub_hlth
       INTO :p19_num_rank,
            :p19_sak_pub_hlth
       FROM t_re_bp_num_rank a,
            t_pub_hlth_pgm b
      WHERE a.sak_pub_hlth = b.sak_pub_hlth
        AND b.cde_pgm_health = 'P19';
    
     if (sqlca.sqlcode != 0)
     {
        fprintf(stderr,"ERROR: could not select from t_re_bp_num_rank\n");
        return EXIT_FAILURE;
     }
 
     sqlca.sqlcode = 0;
     EXEC SQL
     SELECT a.num_rank,
            b.sak_pub_hlth
       INTO :p21_num_rank,
            :p21_sak_pub_hlth
       FROM t_re_bp_num_rank a,
            t_pub_hlth_pgm b
      WHERE a.sak_pub_hlth = b.sak_pub_hlth
        AND b.cde_pgm_health = 'P21';

     if (sqlca.sqlcode != 0)
     {
        fprintf(stderr,"ERROR: could not select from t_re_bp_num_rank\n");
        return EXIT_FAILURE; 
     }
  
     return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   copyBftFields()                                    */
/*                                                                      */
/*  Description:     Every field in the structure is defined as a char. */
/*                   The fields are copied over to the appropriated     */
/*                   data types. (Input KAECSES txns is copied to pgm   */
/*                   variables)                                         */
/*                                                                      */
/* Modification:                                                        */
/* 02/28/06 Srinivas D 8831  Added pres_dis_ind field                   */
/* 05/26/06 Srinivas D 9489  Added elig bypass ind & benefit proration  */
/*                           date fields for pres elig                  */
/* 04/05/09 Srinivas D 11849 Modified to capture the new fields case ssn*/
/*                           case dob, resp person address              */
/************************************************************************/
static void copyBftFields()
{
char amt[10+1];
char ethnic_tmp[10];
char dte[8+1];
char yyyymm_dte[6+1];
char prcnt[3+1];
int i;

    /*The client number comes over as a 10 byte field.  A '1' is placed in the third position to convert it to a beneficiary ID*/
    memcpy(id_medicaid, &currptr->client_number[0], 2);
    id_medicaid[2] = '1';
    memcpy(&id_medicaid[3], &currptr->client_number[2], 8);
    id_medicaid[11] = '\0';

    memset(time_stamp, 0x00, sizeof(time_stamp));
    memcpy(time_stamp, &currptr->date_time_stamp[8], 6);
    time_stamp[6] = '\0';

    memcpy(mid_time, &currptr->date_time_stamp[8], 2);
    mid_time[2] = ':';
    memcpy(&mid_time[3], &currptr->date_time_stamp[10], 2);
    mid_time[5] = ':';
    memcpy(&mid_time[6], &currptr->date_time_stamp[12], 2);
    mid_time[8] = '\0';

    memset(yyyymm_dte, 0x00, sizeof(yyyymm_dte));
    memcpy(yyyymm_dte, currptr->review_due_date, 6);
    yyyymm_dte[6]='\0';
    review_dte = atoi(yyyymm_dte);
    if (review_dte != 0)
    {
       memcpy(dte, yyyymm_dte, sizeof(yyyymm_dte));
       dte[6]='0';
       dte[7]='1';
       dte[8]='\0';
       review_dte_yyyymmdd = atoi(dte);
    }
    else
       review_dte_yyyymmdd = 0;

    memset(prcnt, 0x00, sizeof(prcnt));
    memcpy(prcnt, currptr->percent_poverty_level, 3);
    prcnt[3]='\0';
    percent = atoi(prcnt);

    memset(amt, 0x00, sizeof(amt));
    memcpy(amt, currptr->net_income, sizeof(currptr->net_income));
    net_income = atol(amt);
    net_income /= 100;  /*(net income in input file has 2 decimal places)*/

    memset(client_number, 0x00, sizeof(client_number));
    memcpy(client_number, currptr->client_number,  sizeof(currptr->client_number));

    memset(base_cde_county, 0x00, sizeof(base_cde_county));
    memcpy(base_cde_county, currptr->client_county, sizeof(currptr->client_county));
    base_cde_county[2]='\0';

    memset(retro_t21, 0x00, sizeof(retro_t21));
    memcpy(retro_t21, currptr->retro_t21,  sizeof(currptr->retro_t21));

    memset(med_nam_first, 0x00, sizeof(med_nam_first));
    memcpy(med_nam_first, currptr->med_given_name, sizeof(currptr->med_given_name));
    med_nam_first[12]='\0';

    memset(med_nam_last, 0x00, sizeof(med_nam_last));
    memcpy(med_nam_last, currptr->med_surname, sizeof(currptr->med_surname));
    med_nam_last[17]='\0';

    memset(med_nam_mid, 0x00, sizeof(med_nam_mid));
    memcpy(med_nam_mid, currptr->med_initial, sizeof(currptr->med_initial));
    med_nam_mid[1]='\0';

    memset(case_nam_first, 0x00, sizeof(case_nam_first));
    memcpy(case_nam_first, currptr->case_given_name, sizeof(currptr->case_given_name));
    case_nam_first[12]='\0';

    memset(case_nam_last, 0x00, sizeof(case_nam_last));
    memcpy(case_nam_last, currptr->case_surname, sizeof(currptr->case_surname));
    case_nam_last[17]='\0';

    memset(case_nam_mid, 0x00, sizeof(case_nam_mid));
    memcpy(case_nam_mid, currptr->case_initial, sizeof(currptr->case_initial));
    case_nam_mid[1]='\0';

    memset(nam_first, 0x00, sizeof(nam_first));
    memcpy(nam_first, currptr->client_given_name, sizeof(currptr->client_given_name));

    memset(nam_last, 0x00, sizeof(nam_last));
    memcpy(nam_last, currptr->client_surname, sizeof(currptr->client_surname));

    memset(nam_mid, 0x00, sizeof(nam_mid));
    memcpy(nam_mid, currptr->client_initial, sizeof(currptr->client_initial));

    memset(rp_nam_first, 0x00, sizeof(rp_nam_first));
    memcpy(rp_nam_first, currptr->rp_given_name, sizeof(currptr->rp_given_name));

    memset(rp_nam_last, 0x00, sizeof(rp_nam_last));
    memcpy(rp_nam_last, currptr->rp_surname, sizeof(currptr->rp_surname));

    memset(rp_nam_mid, 0x00, sizeof(rp_nam_mid));
    memcpy(rp_nam_mid, currptr->rp_initial, sizeof(currptr->rp_initial));

    memset(base_adr_street1, 0x00, sizeof(base_adr_street1));
    memcpy(base_adr_street1, currptr->street1, sizeof(currptr->street1));

    memset(base_adr_street2, 0x00, sizeof(base_adr_street2));
    memcpy(base_adr_street2, currptr->street2, sizeof(currptr->street2));

    memset(base_adr_city, 0x00, sizeof(base_adr_city));
    memcpy(base_adr_city, currptr->city, sizeof(currptr->city));

    memset(base_adr_state, 0x00, sizeof(base_adr_state));
    memcpy(base_adr_state, currptr->state, sizeof(currptr->state));

    memset(base_adr_zip_code, 0x00, sizeof(base_adr_zip_code));
    memcpy(base_adr_zip_code, currptr->zip_n, sizeof(currptr->zip_n));

    memset(base_adr_zip_code_4, 0x00, sizeof(base_adr_zip_code_4));
    memcpy(base_adr_zip_code_4, currptr->zip_4, sizeof(currptr->zip_4));

    memset(base_num_ssn, 0x00, sizeof(base_num_ssn));
    memcpy(base_num_ssn, currptr->ssn, sizeof(currptr->ssn));

    memset(base_cde_sex, 0x00, sizeof(base_cde_sex));
    memcpy(base_cde_sex, currptr->sex, sizeof(currptr->sex));

    memset(base_num_phone, 0x00, sizeof(base_num_phone));
    memcpy(base_num_phone, currptr->phone_number, sizeof(currptr->phone_number));

    memset(base_cde_lang_written, 0x00, sizeof(base_cde_lang_written));
    memcpy(base_cde_lang_written, currptr->written_language, sizeof(currptr->written_language));

    memset(base_cde_lang_spoken, 0x00, sizeof(base_cde_lang_spoken));
    memcpy(base_cde_lang_spoken, currptr->spoken_language, sizeof(currptr->spoken_language));

    memset(base_cde_comm_media, 0x00, sizeof(base_cde_comm_media));
    memcpy(base_cde_comm_media, currptr->other_media1, sizeof(currptr->other_media1));

    strncat(base_cde_comm_media, currptr->other_media2, sizeof(currptr->other_media2));

    memset(base_resp_pty_nam_last, 0x00, sizeof(base_resp_pty_nam_last));
    memcpy(base_resp_pty_nam_last, currptr->rp_surname, sizeof(currptr->rp_surname));

    memset(base_resp_pty_nam_first, 0x00, sizeof(base_resp_pty_nam_first));
    memcpy(base_resp_pty_nam_first, currptr->rp_given_name, sizeof(currptr->rp_given_name));

    memset(base_resp_pty_nam_mid, 0x00, sizeof(base_resp_pty_nam_mid));
    memcpy(base_resp_pty_nam_mid, currptr->rp_initial, sizeof(currptr->rp_initial));

    memset(yyyymm_dte, 0x00, sizeof(yyyymm_dte));
    memcpy(yyyymm_dte, currptr->continuous_elig_date, sizeof(currptr->continuous_elig_date));
    yyyymm_dte[6]='\0';
    base_dte_continuous_elg = atoi(yyyymm_dte);
    if (base_dte_continuous_elg != 0)
    {
       memcpy(dte, yyyymm_dte, sizeof(yyyymm_dte));
       dte[6]='0';
       dte[7]='1';
       dte[8]='\0';
       cont_elig = atoi(dte);
    }
    else
       cont_elig = 0;

    memset(yyyymm_dte, 0x00, sizeof(yyyymm_dte));
    memcpy(yyyymm_dte, currptr->benefit_month, sizeof(currptr->benefit_month));
    yyyymm_dte[6]='\0';
    benefit_month_yyyymm = atoi(yyyymm_dte);

    memset(dte, 0x00, sizeof(dte));
    memcpy(dte, currptr->benefit_month, sizeof(currptr->benefit_month));
    dte[6]='0';
    dte[7]='1';
    dte[8]='\0';
    benefit_month_yyyymmdd = atoi(dte);
    id_card_benefit_start = atoi(dte);

    /* for presumptive eligibility records effective date will be on the benefit proration date,
       however, on monthly records the effective date should be first of the benefit month.
       benefit proration will never be changed once it is establised so the benefit month, year and month
       of benefit proration date should be equal for the 1st month of elig, in this case use this as eff date */ 
    if ( (memcmp(currptr->program_type,"MK", sizeof(currptr->program_type)) == 0 ) &&
         (memcmp(currptr->program_subtype, "PE", sizeof(currptr->program_subtype)) == 0 ) &&
         (memcmp(currptr->benefit_month, currptr->benefit_proration_ymd, sizeof(currptr->benefit_month)) == 0))
    {   
          benefit_month_yyyymmdd = atoi(currptr->benefit_proration_ymd);
          id_card_benefit_start  = atoi(currptr->benefit_proration_ymd); 
    }

    memset(base_id, 0x00, sizeof(base_id));
    memcpy(base_id, currptr->cenpay_id, sizeof(currptr->cenpay_id));

    memset(ksc_case_number, 0x00, sizeof(ksc_case_number));
    memcpy(ksc_case_number, currptr->case_number, sizeof(currptr->case_number));

    memset(race_code, 0x00, sizeof(race_code));
    memcpy(race_code, currptr->race_code, 10);
    race_code[10]='\0';

    memset(base_cde_ethnic, 0x00, sizeof(base_cde_ethnic));
    memset(ethnic_tmp, 0x00, sizeof(ethnic_tmp));
    memcpy(ethnic_tmp, &currptr->ethnic_code, sizeof(ethnic_tmp));
    i = 0;
    while (ethnic_tmp[i] == ' ')
    {
      i++;
      if (i == 10)
      {
        i = 0;
        ethnic_tmp[i] = 'N';
        break;
      }
    }

    base_cde_ethnic[0] = ethnic_tmp[i];
    base_cde_ethnic[1]='\0';

    memset(relation_code, 0x00, sizeof(relation_code));
    memcpy(relation_code, currptr->relationship_code, sizeof(currptr->relationship_code));

    memset(amt, 0x00, sizeof(amt));
    memcpy(amt, currptr->t21_premium_amt, sizeof(currptr->t21_premium_amt));
    prem_hw_amt_t21 = atoi(amt);

    memset(amt, 0x00, sizeof(amt));
    memcpy(amt, currptr->working_healthy_prem_amt, sizeof(currptr->working_healthy_prem_amt));
    prem_wh_amt = atoi(amt);

    memset(amt, 0x00, sizeof(amt));
    memcpy(amt, currptr->ms_countable_income, sizeof(currptr->ms_countable_income));
    msIncome = atof(amt);

    memset(worker, 0x00, sizeof(worker));
    memcpy(worker, currptr->worker, sizeof(currptr->worker));

    memset(amt, 0x00, sizeof(amt));
    memcpy(amt, currptr->t21_premium_amt, sizeof(currptr->t21_premium_amt));
    prem_hw_amt_t21 = atoi(amt);

    memset(amt, 0x00, sizeof(amt));
    memcpy(amt, currptr->working_healthy_prem_amt, sizeof(currptr->working_healthy_prem_amt));
    prem_wh_amt = atoi(amt);

    memset(relation_code, 0x00, sizeof(relation_code));
    memcpy(relation_code, currptr->relationship_code, sizeof(currptr->relationship_code));

    memset(dte_time_stamp, 0x00, sizeof(dte_time_stamp));
    memcpy(dte_time_stamp, currptr->date_time_stamp, 14);
    dte_time_stamp[14] = '\0';

    memset(ksc_case_number, 0x00, sizeof(ksc_case_number));
    memcpy(ksc_case_number, currptr->case_number, sizeof(currptr->case_number));
    ksc_case_number[10] = '\0';

    memset(med_subtype, 0x00, sizeof(med_subtype));
    memcpy(med_subtype, currptr->medical_subtype, sizeof(currptr->medical_subtype));
    med_subtype[2]='\0';

    memset(prog_type, 0x00, sizeof(prog_type));
    memcpy(prog_type, currptr->program_type, sizeof(currptr->program_type));
    prog_type[2]='\0';

    memset(qmb_ind, 0x00, sizeof(qmb_ind));
    memcpy(qmb_ind, currptr->qmb_ind, sizeof(currptr->qmb_ind));
    qmb_ind[1]='\0';

    memset(char_dte_birth, 0x00, sizeof(char_dte_birth));
    memcpy(char_dte_birth, currptr->client_date_of_birth, sizeof(currptr->client_date_of_birth));
    base_dte_birth = atoi(char_dte_birth);
   
   /* 14286 - add New field - Dte_application */
    memset(char_dte_application, 0x00, sizeof(char_dte_application));
    memcpy(char_dte_application, currptr->client_date_of_application, sizeof(currptr->client_date_of_application));
    base_dte_application = atoi(char_dte_application);

    memset(legal_status, 0x00, sizeof(legal_status));
    memcpy(legal_status, currptr->legal_status, sizeof(currptr->legal_status));
    legal_status[2]='\0';

    memset(citizen, 0x00, sizeof(citizen));
    memcpy(citizen, currptr->citizenship, sizeof(currptr->citizenship));

    memset(cash_prog, 0x00, sizeof(cash_prog));
    memcpy(cash_prog, currptr->cash_pgm_type, sizeof(currptr->cash_pgm_type));
    cash_prog[2]='\0';

    memset(cash_prog_sb, 0x00, sizeof(cash_prog_sb));
    memcpy(cash_prog_sb, currptr->cash_pgm_subtype, sizeof(currptr->cash_pgm_subtype));
    cash_prog_sb[2]='\0';

    memset(src_fund, 0x00, sizeof(src_fund));
    memcpy(src_fund, currptr->source_of_funding, sizeof(currptr->source_of_funding));
    src_fund[2]='\0';

    memset(med_elig_ind, 0x00, sizeof(med_elig_ind));
    memcpy(med_elig_ind, currptr->medical_elig_ind, sizeof(currptr->medical_elig_ind));
    med_elig_ind[2]='\0';

    memset(prog_subtype, 0x00, sizeof(prog_subtype));
    memcpy(prog_subtype, currptr->program_subtype, sizeof(currptr->program_subtype));
    prog_subtype[2]='\0';
 
    memset(pres_dis_ind, 0x00, sizeof(pres_dis_ind));
    memcpy(pres_dis_ind, currptr->presumptive_dis_ind, sizeof(currptr->presumptive_dis_ind));
    pres_dis_ind[2]='\0';

    memset(part_code, 0x00, sizeof(part_code));
    memcpy(part_code, currptr->participation_code, sizeof(currptr->participation_code));

    memset(placement, 0x00, sizeof(placement));
    memcpy(placement, currptr->type_of_placement, sizeof(currptr->type_of_placement));
    placement[2]='\0';

    memset(amt, 0x00, sizeof(amt));
    memcpy(amt, currptr->spendown_amt, sizeof(currptr->spendown_amt));
    ksc_spend_amt = atof (amt);

    memset(yyyymm_dte, 0x00, sizeof(yyyymm_dte));
    memcpy(yyyymm_dte, currptr->base_st_ym, sizeof(currptr->base_st_ym));
    base_st_ym = atoi(yyyymm_dte);

    memset(yyyymm_dte, 0x00, sizeof(yyyymm_dte));
    memcpy(yyyymm_dte, currptr->base_end_ym, sizeof(currptr->base_end_ym));
    base_end_ym = atoi(yyyymm_dte);

    override_bp_hier = FALSE;
    if (memcmp(currptr->elig_bypass_ind, "Y", sizeof(currptr->elig_bypass_ind)) == 0)
    {
        override_bp_hier = TRUE;
    }

    memset(dte, 0x00, sizeof(dte));
    memcpy(dte, currptr->case_dob_ymd, sizeof(currptr->case_dob_ymd));
    case_dte_birth = atoi(dte);

    memset(case_num_ssn, 0x00, sizeof(case_num_ssn));
    memcpy(case_num_ssn, currptr->case_ssn, sizeof(currptr->case_ssn));

    memset(resp_adr_street1, 0x00, sizeof(resp_adr_street1));
    memcpy(resp_adr_street1, currptr->resp_street1, sizeof(currptr->resp_street1));

    memset(resp_adr_street2, 0x00, sizeof(resp_adr_street2));
    memcpy(resp_adr_street2, currptr->resp_street2, sizeof(currptr->resp_street2));

    memset(resp_adr_city, 0x00, sizeof(resp_adr_city));
    memcpy(resp_adr_city, currptr->resp_city, sizeof(currptr->resp_city));

    memset(resp_adr_state, 0x00, sizeof(resp_adr_state));
    memcpy(resp_adr_state, currptr->resp_state_code, sizeof(currptr->resp_state_code));

    memset(resp_adr_zip_code, 0x00, sizeof(resp_adr_zip_code));
    memcpy(resp_adr_zip_code, currptr->resp_zip_n, sizeof(currptr->resp_zip_n));

    memset(resp_adr_zip_code_4, 0x00, sizeof(resp_adr_zip_code_4));
    memcpy(resp_adr_zip_code_4, currptr->resp_zip_4, sizeof(currptr->resp_zip_4));

 return;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   processELIG ()                                     */
/*                                                                      */
/*  Description:     Reads the KAECSES eligibility file. The fread while*/
/*                   loop reads until a record changes client number    */
/*                   and benefit month.  When the record has changed,   */
/*                   updates are made to the MMIS.                      */
/*                                                                      */
/*  Parameters:      None                                               */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/* CO11182   05/14/2008 Dale Gallaway    Modified the code to call mgd  */
/*                                       function when an eligibility   */
/*                                       record is errored off with an  */
/*                                       issue in spenddown base on the */
/*                                       incoming rec by adding a flag  */
/*                                       end_flag_set_from_processSpndwn*/
/************************************************************************/
static int processElig(FILE *eligFd)
{

char hold_client_number[10+1]="          ";
char hold_benefit_month[6+1]="      ";
char benefit_month[6+1]="      ";
char client[10+1];
char num_spaces[10+1]= "          ";
int linkcnt = 0;
int latest_elig_month;

div_t   count;
time_t  clock;

/* Begin test code */
EXEC SQL BEGIN DECLARE SECTION;
    int test_dte_effective;
    int test_dte_end;
    int test_sak_pub_hlth;
    int test_sak_pgm_elig;
    int maxEff;
EXEC SQL END DECLARE SECTION;
/* End test code */

       /*Assign the address of the elig file to two pointers for link list*/
       headptr = eligrec;
       tempptr = eligrec;

       /* Read ELIG file until eof (end of file) */
       while (fread (eligrec, sizeof(ELIG_REC), 1, eligFd) == 1)
       {
          readcnt++;

          /*write out status message (number of records processed & time)*/
          count = div(readcnt, 1000);

          if (count.rem == 0)
          {
             clock = time (NULL);
             fprintf(stdout,"Records read: [%d], %s\n",readcnt,ctime(&clock));
             fflush(stdout);
          }

          /*skip records already processed in previous run*/
          if (readcnt <= qty_txn_count)
             continue;

          if (next_commit == 0)
             next_commit = readcnt + qty_commit_freq;

          end_process = FALSE;
          bAddedRecord = FALSE;
          updat_aid_flag = FALSE;
          sv_error = 0;
          linkcnt++;

          memset(client, 0x00, sizeof(client));
          memcpy(client, eligrec->client_number, 10);
          client[10] = '\0';

          memset(benefit_month, 0x00, sizeof(benefit_month));
          memcpy(benefit_month, eligrec->benefit_month, 6);
          benefit_month[6] = '\0';

          /*If the hold client number is spaces (which means it is the first read), or client number and benefit month is equal, read next record.  If not equal update MMIS.*/

          if ( ((memcmp(hold_client_number, client, sizeof(hold_client_number)) != 0) &&
               (memcmp(hold_client_number, num_spaces, sizeof(num_spaces)) != 0)) ||
              ((memcmp(hold_client_number, client, sizeof(hold_client_number)) == 0) &&
               (memcmp(hold_benefit_month, benefit_month, sizeof(hold_benefit_month)) != 0)) )
          {

             /*Hierarchy function determines the winning eligibility record that will update the MMIS. Linkcnt - 1 is necessary due to the fact that the program reads until there is a change in client number, therefore there is one extra record in the link list that is not associated with the previous records read*/
             if (hierarchy((linkcnt-1)) != 0)
             {
              fprintf (stderr, "ERROR: bad return from heirarchy\n");
              return EXIT_FAILURE;
             }

             /*If the pop code assignment or benefit plan assignment is invalid, read next record and do not process current records*/
             if (end_process == FALSE)
             {
                /*Check to see if the beneficiary is a new bene or an existing bene*/
                if (checkBaseId() != 0)
                {
	           fprintf (stderr, "ERROR: bad return from checkBaseId\n");
	           return EXIT_FAILURE;
                }

                id_card_printed = FALSE;
                bEqualElig      = FALSE;
                bLowerElig      = FALSE;
                bCaseVerified   = FALSE;
                end_flag_set_from_processSpndwn = FALSE;

                /*If new bene, insert into the MMIS, else if the bene is a crossreference bene update crossreference table, else update the MMIS*/
                if ((sqlca.sqlcode == 100) &&
                    (memcmp(base_id, base_spaces, sizeof(base_id)) == 0))
                {
                   if (insertNewBene() != 0)
                   {
	               fprintf (stderr, "ERROR: bad return from insertNewBene\n");
	               return EXIT_FAILURE;
                   }
                }
                else if (memcmp(base_id, base_spaces, sizeof(base_id)) != 0)
                {
                   if (processXrefId() != 0)
                   {
	              fprintf (stderr, "ERROR: bad return from processXrefId\n");
	              return EXIT_FAILURE;
                   }
                }
                else
                {
                   if (getLatestEligMonth(&latest_elig_month) != 0)
                   {
                     fprintf (stderr, "ERROR: bad return from getLatestEligMonth\n");
                     return EXIT_FAILURE;
                   }


                   if (selectCaseInfo() != 0)
                   {
                     fprintf (stderr, "ERROR: bad return from selectCaseInfo\n");
                     end_process = TRUE;
                   }

                   if (checkValidSpend() != 0)
                   {
                      fprintf (stderr, "ERROR: bad return from checkValidSpend\n");
                      return EXIT_FAILURE;
                   }

                   if (end_process == FALSE && benefitMth() != 0)
                   {
	                  fprintf (stderr, "ERROR: bad return from benefitMth\n");
	                  return EXIT_FAILURE;
                   }

                   if (end_process == FALSE )
                   {
                      if (updateDemoGraphics() != 0)
                      {
                         fprintf (stderr, "ERROR: bad return from updateDemoGraphics\n");
                         return EXIT_FAILURE;
                      }
                      if (processPremBill() != 0)
                      {
                         fprintf (stderr, "ERROR: bad return from processPremBill.1\n");
                         return EXIT_FAILURE;
                      }
                     end_flag_set_from_processSpndwn = FALSE;

                     if ((bAddedRecord == TRUE) ||
                         (bEqualElig   == TRUE &&
                          strncmp(case_number, ksc_case_number, sizeof(case_number)) == 0))
                     {
                        if (updateCountableIncome() != 0)
                        {
                           fprintf (stderr, "ERROR: bad return from updateCountableIncome\n");
                           return EXIT_FAILURE;
                        }

                        if (checkProcessSpndwn() != 0)
                        {
                           fprintf (stderr, "ERROR: bad return from checkProcessSpndwn\n");
                           return EXIT_FAILURE;
                        }
                     }

                      if ((end_process == FALSE) || (end_process == TRUE && end_flag_set_from_processSpndwn == TRUE)) 
                      {
                         num_accept++;

                         /* CO 9195. 
                         if (processKatrina() != 0)
                         {
                            fprintf (stderr, "ERROR: bad return from processKatrina\n");
                            return EXIT_FAILURE;
                         }
                         */

                         /*so mgd_care routine will get it's own sak_aid_elig*/
                         new_sak_aid_elig = 0;

                         if (bAddedRecord == TRUE)
                             sv_error = 0;

                         /*if monthly run, only call mgd_care if input bene month is current month + 1*/
                         if (CALL_MGD_CARE == TRUE && sv_error == 0  &&
                             (runParm == DAILY || benefit_month_yyyymm == curr_dte_pls1mth_yyyymm) &&
                             main_mgd_care_kaecses(currptr, sak_recip, runParm, &new_sak_aid_elig) == 0)
                         {

/* Begin test code */
                             fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);

                             EXEC SQL DECLARE test_re_elig_csr CURSOR FOR
                                 SELECT dte_effective, dte_end, sak_pub_hlth, sak_pgm_elig
                                 FROM t_re_elig
                                 WHERE sak_recip = :sak_recip
                                 AND cde_status1 <> 'H';

                             EXEC SQL OPEN test_re_elig_csr;

                             while (sqlca.sqlcode == 0)
                             {
                                 EXEC SQL FETCH test_re_elig_csr
                                     INTO :test_dte_effective, :test_dte_end,
                                     :test_sak_pub_hlth, test_sak_pgm_elig;

                                 if (sqlca.sqlcode == 0)
                                 {
                                     fprintf(stderr, "\n\n");
                                     fprintf(stderr, "\ndte_effective [%d]", test_dte_effective);
                                     fprintf(stderr, "\ndte_end       [%d]", test_dte_end);
                                     fprintf(stderr, "\nsak_pub_hlth  [%d]", test_sak_pub_hlth);
                                     fprintf(stderr, "\nsak_pgm_elig  [%d]\n", test_sak_pgm_elig);
                                 }
                             }

                             EXEC SQL CLOSE test_re_elig_csr;

/* End test code */

                            fprintf (stderr, "ERROR: bad return from main_mgd_care_kaecses\n");
			    /*return EXIT_FAILURE;*/
                         }
                         bCaseVerified   = FALSE;
                      }
                      else
                        num_reject++;
                   }
                   else
                   { 
                     if ((bAddedRecord == TRUE) ||
                         (bEqualElig   == TRUE &&
                          strncmp(case_number, ksc_case_number, sizeof(case_number)) == 0))
                     {
                        if (checkProcessSpndwn() != 0)
                        {
                           fprintf (stderr, "ERROR: bad return from checkProcessSpndwn\n");
                           return EXIT_FAILURE;
                        }
                     }
                     num_reject++;
                   }
                }
             }
            
	     /*if it's time to commit and */
             /*client changed and is not 1st record... */
             if ( readcnt >= next_commit &&
                  memcmp(hold_client_number, client, sizeof(hold_client_number)) != 0 &&
                  memcmp(hold_client_number, num_spaces, sizeof(num_spaces)) != 0 )
             {
                /*...commit changes*/
                /*(for all records read except current one)*/
                qty_txn_count = readcnt - 1;

                if (commitElig() != 0)
                {
                   fprintf (stderr, "ERROR: bad return from commitElig\n");
                   return EXIT_FAILURE;
                }

                next_commit = readcnt + qty_commit_freq;
             }

             /*Move data to hold variables to compare the next record read in*/

             memcpy(hold_client_number, client, sizeof(hold_client_number));
             memcpy(hold_benefit_month, benefit_month, sizeof(hold_benefit_month));
             /* prev_sak_recip = sak_recip;    jcw mvc to getSakPopCode() */
             sak_case = 0;
             sak_recip = 0;

             /*After the records are processed, clean up the link list to read another set of records*/
             currptr = headptr;

             /*Linkcnt -1 is necessary because records are read in until the client number or benefit */
             /*month is different.  Therefore, there is one extra record in the link list that is not */
             /*associated with the previous records.  The record left will be used to compare the next*/
             /*records read in */
             while ((linkcnt-1) != 0)
             {
               tempptr = currptr->next;
               free (currptr);
               currptr = tempptr;
               linkcnt --;
             }

             /*Reassign the record's address that is left to the two pointers*/
             headptr = currptr;
             eligrec = currptr;

          }
          else
          {
             /*Move data to hold variables to compare the next record read in*/

             memcpy(hold_client_number, client, sizeof(hold_client_number));
             memcpy(hold_benefit_month, benefit_month, sizeof(hold_benefit_month));
          }

         /*Allocate memory to read another record*/
          tempptr = eligrec;

          if (allocMemory() != 0)
          {
             fprintf (stderr, "ERROR: bad return from allocMemory\n");
             return EXIT_FAILURE;
          }

          tempptr->next = eligrec;

       }/* End of while loop */

    /*The process below is done on the last records or record, depending on if they are duplicates*/
    if (linkcnt > 0)
    {
       end_process = FALSE;
       sv_error = 0;
       if (hierarchy((linkcnt)) != 0)
          {
             fprintf (stderr, "ERROR: bad return from heirarchy\n");
             return EXIT_FAILURE;
          }

          if (end_process == FALSE)
          {
             if (checkBaseId() != 0)
             {
                fprintf (stderr, "ERROR: bad return from checkBaseId\n");
                return EXIT_FAILURE;
             }

             id_card_printed = FALSE;

             if ((sqlca.sqlcode == 100) &&
                 (memcmp(base_id, base_spaces, sizeof(base_id)) == 0))
             {
                if (insertNewBene() != 0)
                {
                   fprintf (stderr, "ERROR: bad return from insertNewBene\n");
                   return EXIT_FAILURE;
                 }
             }
             else if (memcmp(base_id, base_spaces, sizeof(base_id)) != 0)
             {
                 if (processXrefId() != 0)
                 {
                    fprintf (stderr, "ERROR: bad return from processXrefId\n");
                    return EXIT_FAILURE;
                 }
             }
             else
             {
                 if (getLatestEligMonth(&latest_elig_month) != 0)
                 {
                    fprintf (stderr, "ERROR: bad return from getLatestEligMonth\n");
                    return EXIT_FAILURE;
                 }

                 if (selectCaseInfo() != 0)
                 {
                   fprintf (stderr, "ERROR: bad return from selectCaseInfo\n");
                   end_process = TRUE;
                 }

                 if (checkValidSpend() != 0)
                 {
                    fprintf (stderr, "ERROR: bad return from checkValidSpend\n");
                    return EXIT_FAILURE;
                 }

                 if (end_process == FALSE && benefitMth() != 0)
                 {
                    fprintf (stderr, "ERROR: bad return from benefitMth\n");
                    return EXIT_FAILURE;
                 }

                 if (end_process == FALSE )
                 {
                    if (updateDemoGraphics() != 0)
                    {
                       fprintf (stderr, "ERROR: bad return from updateDemoGraphics\n");
                       return EXIT_FAILURE;
                    }
                    if (processPremBill() != 0)
                    {
                       fprintf (stderr, "ERROR: bad return from processPremBill.2\n");
                       return EXIT_FAILURE;
                    }
                     end_flag_set_from_processSpndwn = FALSE;      

                     if ((bAddedRecord == TRUE) ||
                         (bEqualElig   == TRUE &&
                          strncmp(case_number, ksc_case_number, sizeof(case_number)) == 0))
                     {
                       if (updateCountableIncome() != 0)
                       {
                          fprintf (stderr, "ERROR: bad return from updateCountableIncome\n");
                          return EXIT_FAILURE;
                       }
                 
                        if (checkProcessSpndwn() != 0)
                        {
                           fprintf (stderr, "ERROR: bad return from checkProcessSpndwn\n");
                           return EXIT_FAILURE;
                        }
                     }

                     if ((end_process == FALSE) || (end_process == TRUE && end_flag_set_from_processSpndwn == TRUE)) 
                     {
                        num_accept++;

                        /* CO 9195
                         if (processKatrina() != 0)
                         {
                            fprintf (stderr, "ERROR: bad return from processKatrina\n");
                            return EXIT_FAILURE;
                         }
                        */

                        /*so mgd_care routine will get it's own sak_aid_elig*/
                        new_sak_aid_elig = 0;

                        if (bAddedRecord == TRUE)
                            sv_error = 0;

                        /*if monthly run, only call mgd_care if input bene month is current month + 1*/
                        if (CALL_MGD_CARE == TRUE && sv_error == 0  &&
                            (runParm == DAILY || benefit_month_yyyymm == curr_dte_pls1mth_yyyymm) &&
                            main_mgd_care_kaecses(currptr, sak_recip, runParm, &new_sak_aid_elig) == 0)
                        {
/* Begin test code */
                            fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);

                            fprintf(stderr, "\nLast Record!\n");
                            EXEC SQL DECLARE test1_re_elig_csr CURSOR FOR
                                SELECT dte_effective, dte_end, sak_pub_hlth, sak_pgm_elig
                                FROM t_re_elig
                                WHERE sak_recip = :sak_recip
                                AND cde_status1 <> 'H';

                            EXEC SQL OPEN test1_re_elig_csr;

                            while (sqlca.sqlcode == 0)
                            {
                                EXEC SQL FETCH test1_re_elig_csr
                                    INTO :test_dte_effective, :test_dte_end,
                                    :test_sak_pub_hlth, test_sak_pgm_elig;

                                if (sqlca.sqlcode == 0)
                                {
                                    fprintf(stderr, "\n\n");
                                    fprintf(stderr, "\ndte_effective [%d]", test_dte_effective);
                                    fprintf(stderr, "\ndte_end       [%d]", test_dte_end);
                                    fprintf(stderr, "\nsak_pub_hlth  [%d]", test_sak_pub_hlth);
                                    fprintf(stderr, "\nsak_pgm_elig  [%d]\n", test_sak_pgm_elig);
                                }
                            }

                            EXEC SQL CLOSE test1_re_elig_csr;

/* End test code */

                           fprintf (stderr, "ERROR: bad return from main_mgd_care_kaecses\n");
		           /*return EXIT_FAILURE;*/
                        }
                     }
                     else
                        num_reject++;
                  }
                  else
                  { 
                     if ((bAddedRecord == TRUE) ||
                         (bEqualElig   == TRUE &&
                          strncmp(case_number, ksc_case_number, sizeof(case_number)) == 0))
                     {
                        if (checkProcessSpndwn() != 0)
                        {
                           fprintf (stderr, "ERROR: bad return from checkProcessSpndwn\n");
                           return EXIT_FAILURE;
                        }
                     }
                     num_reject++;
                  } 
               }
            }
           
	   currptr = headptr;

           while (currptr->next != NULL)
           {
             tempptr = currptr->next;
             free (currptr);
             currptr = tempptr;
          }
       }

       /*reset restart count*/
       qty_txn_count = 0;

       if (commitElig() != 0)
       {
          fprintf (stderr, "ERROR: bad return from commitElig\n");
          return EXIT_FAILURE;
       }

       /*Display total number of records read in the execution of the job*/
       fprintf(stdout,"Total number of ELIG records read: %d\n", readcnt);

       return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   allocMemory()                                      */
/*                                                                      */
/*  Description:    Allocating a block of memory for the ELIG file.     */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int allocMemory()
{

 if ( (eligrec = malloc(sizeof(ELIG_REC)) ) == NULL)
 {
    fprintf(stderr, "ERROR: Memory Allocation Falilure.\n");
    return EXIT_FAILURE;
 }

 eligrec->next = NULL;

 return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   processKatrina()                                   */
/*                                                                      */
/*  Description:    Adds row for Hurricane Katrina beneficiaries.       */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO8511   09/14/2005 Lisa Salgat      Initial Release                */
/************************************************************************/
static int processKatrina()
{
char ktrWorker[2+1] = "33\0";
int ktrNumRank   = 130;
int temp_dte_eff = 0;
int temp_dte_end = 0;
int temp_sak_pgm = 0;
int bMatch         = FALSE;

exec sql begin declare section;
int ktrDteEff     = 0;
int ktrDteEnd     = 0;
int ktrSakPgmElig = 0;
exec sql end declare section;

   if ((worker[2] == '8') ||
       (worker[3] == '8'  &&
        strncmp(worker, ktrWorker, 2) == 0))
   {
     if (bFirstTime == TRUE)
     {
        bFirstTime = FALSE;

        EXEC SQL SELECT sak_mc_entity
                  INTO :sql_sak_mc_entity
                  FROM t_mc_entity
                 WHERE cde_mc_entity = 'KCS';

        if (sqlca.sqlcode != 0)
        {
           fprintf(stderr,"ERROR: could not select from t_mc_entity\n");
           return EXIT_FAILURE;
        }

        EXEC SQL SELECT sak_spec_cond
                   INTO :sql_HCK_cond_code
                   FROM t_mc_spec_cond
                  WHERE cde_spec_cond_type = 'XCL'
                    AND sak_pub_hlth       = 32;

        if (sqlca.sqlcode != 0)
        {
           fprintf(stderr,"ERROR: could not select from t_mc_spec_cond for HCK\n");
           return EXIT_FAILURE;
        }

        EXEC SQL SELECT sak_spec_cond
                   INTO :sql_HW19_cond_code
                   FROM t_mc_spec_cond
                  WHERE cde_spec_cond_type = 'XCL'
                    AND sak_pub_hlth       = 15;

        if (sqlca.sqlcode != 0)
        {
           fprintf(stderr,"ERROR: could not select from t_mc_spec_cond for HW19\n");
           return EXIT_FAILURE;
        }

        EXEC SQL SELECT sak_pub_hlth
                   INTO :ktr_pub_hlth
                   FROM t_pub_hlth_pgm
                  WHERE cde_pgm_health = 'HK';

        if (sqlca.sqlcode != 0)
        {
           fprintf(stderr,"ERROR: could not select sak_pub_hlth for HK beneficiary\n");
           return EXIT_FAILURE;
        }
     }

      EXEC SQL
      DECLARE KTR_CSR CURSOR FOR
        SELECT dte_effective,
               dte_end,
               sak_pgm_elig
          FROM t_re_elig
         WHERE sak_recip    = :sak_recip
           AND sak_pub_hlth = :ktr_pub_hlth
           AND cde_status1 <> 'H'
           AND (:benefit_month_yyyymmdd BETWEEN dte_effective AND dte_end
            OR  :benefit_month_yyyymmdd = to_number(to_char(to_date(dte_end, 'YYYYMMDD') +1, 'YYYYMMDD'))
            OR  :benefit_month_lastday  = to_number(to_char(to_date(dte_effective, 'YYYYMMDD') -1, 'YYYYMMDD')))
       ORDER BY dte_effective, dte_end;

      EXEC SQL OPEN KTR_CSR;

      if (sqlca.sqlcode != 0)
      {
         fprintf (stderr, "ERROR: could not open KTR_CSR\n");
         fprintf (stderr, "ERROR: Sak Recip %d, sqlca.sqlcode %d\n", sak_recip, sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      EXEC SQL FETCH KTR_CSR
        INTO :ktrDteEff,
             :ktrDteEnd,
             :ktrSakPgmElig;

      elig_sak_recip = sak_recip;

      if (sqlca.sqlcode == 100)
      {
        if (insertEligSeg(75, ktrNumRank) != 0)
        {
           fprintf (stderr, "ERROR: bad return from insertEligSeg\n");
           return EXIT_FAILURE;
        }
     }
     else
     {
        while (sqlca.sqlcode == 0 && bMatch == FALSE)
        {
           if (benefit_month_yyyymmdd >= ktrDteEff &&
               benefit_month_lastday  <= ktrDteEnd)
           {
              bMatch = TRUE;
           }
           else if (benefit_month_yyyymmdd > ktrDteEnd)
           {
              temp_dte_eff = ktrDteEff;
              temp_dte_end = benefit_month_lastday;
              temp_sak_pgm = ktrSakPgmElig;
           }
           else if (benefit_month_lastday < ktrDteEff)
           {
              temp_dte_eff = benefit_month_yyyymmdd;
              temp_dte_end = ktrDteEnd;
              temp_sak_pgm = ktrSakPgmElig;
           }

         EXEC SQL FETCH KTR_CSR
           INTO :ktrDteEff,
                :ktrDteEnd,
                :ktrSakPgmElig;
        }

        if (sqlca.sqlcode != 100 && bMatch == FALSE)
        {
           fprintf (stderr, "ERROR: bad fetch from KTR_CSR\n");
           fprintf (stderr, "ERROR: Sak Recip %d, sqlca.sqlcode %d\n", sak_recip, sqlca.sqlcode);
           return EXIT_FAILURE;
        }

        if (bMatch == FALSE)
        {
           if (updateElig(temp_dte_eff, temp_dte_end, temp_sak_pgm) != 0)
           {
              fprintf (stderr, "ERROR: bad return from updateElig\n");
              return EXIT_FAILURE;
           }
        }
     }

     EXEC SQL CLOSE KTR_CSR;

     if (sqlca.sqlcode != 0)
     {
        fprintf (stderr, "ERROR: could not close KTR_CSR\n");
        fprintf (stderr, "ERROR: Sak Recip %d, sqlca.sqlcode %d\n", sak_recip, sqlca.sqlcode);
        return EXIT_FAILURE;
     }

     /* Run once for HCK  */
     if (processMCSpecCon(sql_HCK_cond_code, "HCK") != 0)
     {
        fprintf (stderr, "ERROR: bad return from processMCSpecCon\n");
        return EXIT_FAILURE;
     }

     /* Run once for HW19 */
     if (processMCSpecCon(sql_HW19_cond_code, "HW19") != 0)
     {
        fprintf (stderr, "ERROR: bad return from processMCSpecCon\n");
        return EXIT_FAILURE;
     }
   }

   return EXIT_SUCCESS;

} /* end processKatrina */

/************************************************************************/
/*                                                                      */
/*  Function Name:   processMCSpecCon()                                 */
/*                                                                      */
/*  Description:    Adds row to t_mc_spec_cond table for Katrina Benes. */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO8511   09/14/2005 Lisa Salgat      Initial Release                */
/************************************************************************/
static int processMCSpecCon(int condition_code, char prog_type[])
{
exec sql begin declare section;
  int sql_sak_spec_cond     = condition_code;
  int sql_derived_sak_short = 0;
  int sql_sak_recip         = 0;
exec sql end declare section;

  /* check to see if the beneficiary already had special conditions row for benefit month */

   EXEC SQL SELECT sak_recip
              INTO :sql_sak_recip
              FROM t_mc_re_spec_cond
             WHERE sak_recip      = :sak_recip
               AND sak_spec_cond  = :sql_sak_spec_cond
               AND dte_effective <= :benefit_month_lastday
               AND dte_end        > :benefit_month_yyyymmdd
               AND cde_status1   <> 'H';

   /* If the sql not found then we need to add a row to the table  */

   if (sqlca.sqlcode != 0 && sqlca.sqlcode != 100)
   {
      fprintf (stderr, " ERROR: could not determine t_mc_re_spec_cond for sak_recip = [%d] and sak_spec_cond = [%d]\n",
               sak_recip, sql_sak_spec_cond );
      return EXIT_FAILURE;
   }
   else if ( sqlca.sqlcode == 100 )
   {
      EXEC SQL SELECT NVL(MAX(sak_short + 1), 1)
                 INTO :sql_derived_sak_short
                 FROM t_mc_re_spec_cond
                WHERE sak_recip = :sak_recip;

      if (sqlca.sqlcode != 0)
      {
         fprintf (stderr, " ERROR: could not retrieve sak_short for bene [%d] from t_mc_rc_spec_cond table for %s.\n",
                    sak_recip, prog_type);
         return EXIT_FAILURE;
      }

      EXEC SQL  INSERT INTO t_mc_re_spec_cond
                            (sak_recip,
                             sak_short,
                             sak_spec_cond,
                             dte_effective,
                             dte_end,
                             cde_status1,
                             dte_added,
                             sak_mc_ent_add,
                             dte_change,
                             sak_mc_ent_change,
                             dte_termed,
                             sak_mc_ent_term,
                             dte_history,
                             sak_mc_ent_hist)
                     VALUES
                            (:sak_recip,
                             :sql_derived_sak_short,
                             :sql_sak_spec_cond,
                             :benefit_month_yyyymmdd,
                             20060331,
                             ' ',
                             :curr_dte,
                             :sql_sak_mc_entity,
                             0, 0, 0, 0, 0, 0);

      if (sqlca.sqlcode != 0)
      {
         fprintf (stderr, " ERROR: could not insert bene [%d] into t_mc_rc_spec_cond table for %s.\n",
                  sak_recip, prog_type);
         return EXIT_FAILURE;
      }

      EXEC SQL
         UPDATE t_mc_re_spec_cond
            SET cde_status1= 'H'
          WHERE sak_recip      = :sak_recip
            AND sak_spec_cond  = :sql_sak_spec_cond
            AND dte_effective  > :benefit_month_yyyymmdd;

      if (sqlca.sqlcode != 0 && sqlca.sqlcode != 100)
      {
         fprintf (stderr, " ERROR: could not update bene [%d] on t_mc_rc_spec_cond table for %s.\n",
                  sak_recip, prog_type);
         return EXIT_FAILURE;
      }
   }

   return EXIT_SUCCESS;

} /* end processMCSpecCond */

/************************************************************************/
/*                                                                      */
/*  Function Name:   updateDemoGraphics()                               */
/*                                                                      */
/*  Description:    Updates demographic information for base and case   */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO8306   08/17/2005 Lisa Salgat      Initial Release                */
/*  CO11849  04/05/2009 Srini Donepudi   Modified to update the resp.   */
/*                                       person address.                */
/*  CO12846  07/30/2010 Dale Gallaway    Change "&&" to "||" on the     */
/*                                       AS-FC logic for med_nam_first, */
/*                                       med_nam_last.                  */
/************************************************************************/
static int updateDemoGraphics()
{
char l_spaces[17+1]   = "                 ";
char f_spaces[12+1]   = "            ";
char hold_first[12+1] = " ";
char hold_last[17+1]  = " ";
char hold_mid[1+1]    = " ";
char bUpdateDemo      = TRUE;

   /*only update changed case/base data for current/new bene months*/
   if (bActiveElig == TRUE)
   {
      if (bCaseVerified == FALSE )
      {
         if (selectCaseInfo() != 0)
         {
            fprintf (stderr, "ERROR: bad return from selectCaseInfo\n");
            return EXIT_FAILURE;
         }

         if (strncmp(case_number, ksc_case_number, sizeof(case_number)) != 0)
         {
            bUpdateDemo = FALSE;
         }
         else
         {
            /*If the bene's case med information is not equal to spaces, */
            /*use that name to update the case head information, else use*/
            /*the bene's case information                                */

            memcpy(hold_first, case_nam_first, sizeof(case_nam_first));
            memcpy(hold_last,  case_nam_last,  sizeof(case_nam_last));
            memcpy(hold_mid,   case_nam_mid,   sizeof(case_nam_mid));

            if ((memcmp(cash_prog, "AS", sizeof(cash_prog))== 0) ||
                (memcmp(cash_prog, "FC", sizeof(cash_prog))== 0))
            {
               if ((memcmp(med_nam_first, f_spaces, sizeof(med_nam_first)) != 0) ||
                   (memcmp(med_nam_last, l_spaces, sizeof(med_nam_last)) != 0))
               {
                  memcpy(hold_first, med_nam_first, sizeof(med_nam_first));
                  memcpy(hold_last,  med_nam_last,  sizeof(med_nam_last));
                  memcpy(hold_mid,   med_nam_mid,   sizeof(med_nam_mid));
               }
            }

            if (verifyCaseInfo(hold_first, hold_last, hold_mid) != 0)
            {
               fprintf (stderr, "ERROR: bad return from verifyCaseInfo\n");
               return EXIT_FAILURE;
            }
         }
      }

      if (bUpdateDemo == TRUE)
      {
         if (processBase() != 0)
         {
            fprintf (stderr, "ERROR: bad return from processBase\n");
            return EXIT_FAILURE;
         }

         if (previousInfo() != 0)
         {
            fprintf (stderr, "ERROR: bad return from previousInfo\n");
            return EXIT_FAILURE;
         }
         if (respAddress() != 0)
         {
            fprintf (stderr, "ERROR: bad return from respAddress\n");
            return EXIT_FAILURE;
         }
 
      }
   }

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   checkBaseId()                                      */
/*                                                                      */
/*  Description:    Check the t_re_base table to see if the bene is an  */
/*                  existing bene or a new bene.                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int checkBaseId()
{

      sqlca.sqlcode = 0;
      EXEC SQL
        SELECT sak_recip,
               nam_last,
               nam_first,
               nam_mid_init,
               adr_street_1,
               adr_street_2,
               adr_city,
               adr_state,
               adr_zip_code,
               adr_zip_code_4,
               cde_county,
               cde_office,
               dte_death
        INTO   :sak_recip,
               :prev_nam_last,
               :prev_nam_first,
               :prev_nam_mid,
               :prev_adr_street1,
               :prev_adr_street2,
               :prev_adr_city,
               :prev_adr_state,
               :prev_adr_zip_code,
               :prev_adr_zip_code_4,
               :prev_cde_county,
               :prev_cde_office,
               :dte_death
        FROM  t_re_base
        WHERE id_medicaid = :id_medicaid;

      if((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
      {
        fprintf(stderr,"ERROR: could not select sak recip from t_re_base\n");
        fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr,"ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
      }

      return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertNewBene()                                    */
/*                                                                      */
/*  Description:    This function is for new benes.                     */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO12846  07/30/2010 Dale Gallaway    Change "&&" to "||" on the     */
/*                                       AS-FC logic for med_nam_first, */
/*                                       med_nam_last.                  */
/************************************************************************/
static int insertNewBene()
{

exec sql begin declare section;
char l_spaces[17+1] = "                 ";
char f_spaces[12+1] = "            ";
char hold_first[12+1]=" ";
char hold_last[17+1]=" ";
char hold_mid[1+1]=" ";
int temp_dte_eff;
exec sql end declare section;

    bValidSpendRec = TRUE;
    /*If the bene's case med information is not equal to spaces, */
    /*use that name to update the case head information, else use*/
    /*the bene's case information                                */

    memcpy(hold_first, case_nam_first, sizeof(case_nam_first));
    memcpy(hold_last,  case_nam_last,  sizeof(case_nam_last));
    memcpy(hold_mid,   case_nam_mid,   sizeof(case_nam_mid));

    if ((memcmp(cash_prog, "AS", sizeof(cash_prog))== 0) ||
        (memcmp(cash_prog, "FC", sizeof(cash_prog))== 0))
    {
       if ((memcmp(med_nam_first, f_spaces, sizeof(med_nam_first)) != 0) ||
           (memcmp(med_nam_last, l_spaces, sizeof(med_nam_last)) != 0))
       {
          memcpy(hold_first, med_nam_first, sizeof(med_nam_first));
          memcpy(hold_last,  med_nam_last,  sizeof(med_nam_last));
          memcpy(hold_mid,   med_nam_mid,   sizeof(med_nam_mid));
       }
    }

    /*Verify that the case number assigned to the bene is currently in the system*/
    if (verifyCaseNum() != 0)
    {
       fprintf (stderr, "ERROR: bad return from verifyCaseNum\n");
       return EXIT_FAILURE;
    }

    /*If the case number is not currently in the system, insert the case number into the case tables*/
    if (sqlca.sqlcode == 100)
    {
       if (insertCase(hold_first, hold_last, hold_mid) != 0)
       {
          fprintf (stderr, "ERROR: bad return from insertCase\n");
          return EXIT_FAILURE;
       }
    }
    else
    {
       bene_sak_case = sak_case;
       /*If the case number is currently in the system, verify that the information is still the same*/
       if (verifyCaseInfo(hold_first, hold_last, hold_mid) != 0)
       {
          fprintf (stderr, "ERROR: bad return from verifyCaseInfo\n");
          return EXIT_FAILURE;
       }
    }

    if (getNewSakRecip() != 0)
    {
       fprintf (stderr, "ERROR: bad return from getNewSakRecip\n");
       return EXIT_FAILURE;
    }

    elig_sak_recip = new_sak_recip;
    sak_recip = new_sak_recip;
    bCaseVerified = TRUE;
    case_dte_cert = benefit_month_yyyymmdd;

    /*If the new bene is a T21 bene without retro flag,*/
    /*effective date is parm date plus 1*/
    if ((sak_pub_hlth[0] == 4 && retro_t21[0] != 'Y') &&
        (benefit_month_yyyymm == curr_dte_yyyymm))
    {
       temp_dte_eff = benefit_month_yyyymmdd;
       benefit_month_yyyymmdd = parm_dte_pls1;
       id_card_benefit_start = parm_dte_pls1;
       case_dte_cert = benefit_month_yyyymmdd;

       if (insertElig(bene_sak_case) != 0)
       {
          fprintf(stderr, "ERROR: bad return from insertElig\n");
          return EXIT_FAILURE;
       }

       benefit_month_yyyymmdd = temp_dte_eff;
    }
    else
    {
       if (insertElig(bene_sak_case) != 0)
       {
          fprintf (stderr, "ERROR: bad return from insertElig\n");
          return EXIT_FAILURE;
       }
    }

    if (insertCaseXref(case_dte_cert, OPEN_DATE, "C") != 0)
    {
       fprintf (stderr, "ERROR: bad return from insertCaseXref\n");
       return EXIT_FAILURE;
    }

    if (processPremBill() != 0)
    {
        fprintf (stderr, "ERROR: bad return from processPremBill.3\n");
        return EXIT_FAILURE;
    }

    if (updateCountableIncome() != 0)
    {
       fprintf (stderr, "ERROR: bad return from updateCountableIncome\n");
       return EXIT_FAILURE;
    }

    if (insertRace() != 0)
    {
       fprintf (stderr, "ERROR: bad return from insertRace\n");
       return EXIT_FAILURE;
    }

    if (checkProcessSpndwn() != 0)
    {
       fprintf (stderr, "ERROR: bad return from checkProcessSpndwn\n");
       return EXIT_FAILURE;
    }

    /*If the bene is under 21 update EPSDT information*/
    if (epsdt_age < 21)
    {
       if (updateEpsdtLet() != 0)
       {
          fprintf (stderr, "ERROR: bad return from updateEpsdtLet\n");
          return EXIT_FAILURE;
       }
    }

    if (insertBase() != 0)
    {
       fprintf (stderr, "ERROR: bad return from insertBase\n");
       return EXIT_FAILURE;
    }

    if (insertTransportLevel(DEFAULT_START_DATE, new_sak_recip, 0) != 0)
    {
       fprintf (stderr, "ERROR: bad return from insertTransportLevel\n");
       return EXIT_FAILURE;
    }   /* 11961 */ /* 14538 - Backed out 11961 */

    if (insertRespAddress() != 0)
    {
         fprintf (stderr, "ERROR: bad return from insertRespAddress.\n");
         return EXIT_FAILURE;
    }

    num_accept++;
    /* CO 9195
    if (processKatrina() != 0)
    {
       fprintf (stderr, "ERROR: bad return from processKatrina\n");
       return EXIT_FAILURE;
    }
    */

    /*so mgd_care routine will get it's own sak_aid_elig*/
    new_sak_aid_elig = 0;

    /*if monthly run, only call mgd_care if input bene month is current month + 1*/
    if (CALL_MGD_CARE == TRUE &&
        (runParm == DAILY || benefit_month_yyyymm == curr_dte_pls1mth_yyyymm) &&
        main_mgd_care_kaecses(currptr, sak_recip, runParm, &new_sak_aid_elig) == 0)
    {
       fprintf (stderr, "ERROR: bad return from main_mgd_care_kaecses\n");
       /*return EXIT_FAILURE;*/
    }
    
   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertBase()                                       */
/*                                                                      */
/*  Description:    Insert Base information.                            */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int insertBase()
{

exec sql begin declare section;
char cde_soundex[4+1];
char space[1+1] = " ";
char base_ind_active = 'Y';
int zero = 0;
int sak_citizen_dsc = 0;
int sak_cnty_off_srv = 0;
exec sql end declare section;

     dte_death = 0;

     sqlca.sqlcode = 0;
     EXEC SQL
       SELECT soundex(:nam_last)
       INTO   :cde_soundex
       FROM   dual;

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not select soundex on last name\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

     sqlca.sqlcode = 0;
     EXEC SQL
       SELECT sak_citizen_dsc
       INTO   :sak_citizen_dsc
       FROM   t_re_citizen_dsc
       WHERE  cde_citizen_stat = :citizen;

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not select sak citizen from t_re_citizen_dsc\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

     sqlca.sqlcode = 0;
     EXEC SQL
       SELECT sak_cnty_off
       INTO   :sak_cnty_off_srv
       FROM   t_county_office
       WHERE  cde_county = :base_cde_county
       AND    cde_office = '1';

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not select sak cnty off from t_county_office\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      /* Latitude/Longitude trigger disabled. Get here. */
      if( calculateLatLong(base_adr_street1, base_adr_city, base_adr_state,
                           base_adr_zip_code, base_adr_zip_code_4,
                           &base_lat, &base_long, &base_geo_qual
                          ) != 0
        )
          return EXIT_FAILURE;

     sqlca.sqlcode = 0;
     EXEC SQL
       INSERT INTO t_re_base
              (sak_recip,
               sak_case,
               id_medicaid,
               nam_last,
               nam_first,
               nam_mid_init,
               adr_street_1,
               adr_street_2,
               adr_city,
               adr_state,
               adr_zip_code,
               adr_zip_code_4,
               num_latitude,
               num_longitude,
               cde_gis_quality,
               num_ssn,
               dte_birth,
               dte_death,
               cde_sex,
               cde_ethnic,
               cde_marital,
               cde_county,
               cde_office,
               sak_cnty_off_srv,
               ind_mny_grant,
               cde_facility,
               ind_suspect,
               cde_ward_type,
               cde_county_ward,
               num_phone,
               sak_cde_phone,
               sak_citizen_dsc,
               ind_spec_hlth,
               cde_soundex,
               ind_active,
               cde_lang_written,
               cde_lang_spoken,
               cde_comm_media,
               resp_pty_nam_last,
               resp_pty_nam_first,
               resp_pty_nam_mi,
               dte_cred_covg_cert,
               dte_continuous_elg,
               dte_application,
               ind_addr_invalid)
       VALUES (:new_sak_recip,
               :bene_sak_case,
               :id_medicaid,
               :nam_last,
               :nam_first,
               :nam_mid,
               :base_adr_street1,
               :base_adr_street2,
               :base_adr_city,
               :base_adr_state,
               :base_adr_zip_code,
               :base_adr_zip_code_4,
               :base_lat,
               :base_long,
               :base_geo_qual,
               :base_num_ssn,
               :base_dte_birth,
               :zero,
               :base_cde_sex,
               :base_cde_ethnic,
               :space,
               :base_cde_county,
               '1',
               :sak_cnty_off_srv,
               'N',
               'N',
               :space,
               'N',
               :space,
               :base_num_phone,
               '6',
               :sak_citizen_dsc,
               :base_ind_spec_hlth,
               :cde_soundex,
               :base_ind_active,
               :base_cde_lang_written,
               :base_cde_lang_spoken,
               :base_cde_comm_media,
               :rp_nam_last,
               :rp_nam_first,
               :rp_nam_mid,
               :zero,
               :cont_elig,
               to_date(:base_dte_application,'YYYYMMDD'),
               'N');

         if (sqlca.sqlcode != 0)
         {
            fprintf(stderr, "ERROR: could not insert into t_re_base\n");
            fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
            fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
            return EXIT_FAILURE;
         }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertTransportLevel()                             */
/*                                                                      */
/*  Description:    Insert Default Transportation level information.    */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO6930   07/26/2004 Lisa Salgat      Initial Release                */
/************************************************************************/
static int insertTransportLevel(int start_date, int tmp_sak_recip, int tmp_sak_short)
{

exec sql begin declare section;
char default_transp_lvl[2+1]= "L1";
int transp_sak_short  = tmp_sak_short;
int transp_start_date = start_date;
int transp_sak_recip  = tmp_sak_recip;
int open_date = OPEN_DATE;
exec sql end declare section;

transp_sak_short++;

     sqlca.sqlcode = 0;
     EXEC SQL
       INSERT INTO t_re_transport_level
              (sak_recip,
               sak_short,
               cde_transport_level,
               dte_effective,
               dte_end,
               dte_created,
               dte_last_updated)
       VALUES (:transp_sak_recip,
               :transp_sak_short,
               :default_transp_lvl,
               :transp_start_date,
               :open_date,
               :curr_dte,
               :curr_dte);

     if (sqlca.sqlcode != 0)
     {
        fprintf(stderr, "ERROR: could not insert into t_re_transport_level\n");
        fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }

     return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   validateTransportLevel()                           */
/*                                                                      */
/*  Description:    Verify bene with gap in eligibility does not have   */
/*                  current transportation level segment.               */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO6930   11/04/2004 Lisa Salgat      Initial Release                */
/************************************************************************/
static int validateTransportLevel()
{

exec sql begin declare section;
int transp_dte_effective;
int transp_sak_short;
int new_start_date;
exec sql end declare section;

     sqlca.sqlcode = 0;
     EXEC SQL
       SELECT dte_effective
         INTO :transp_dte_effective
         FROM t_re_transport_level
        WHERE sak_recip = :sak_recip
          AND :benefit_month_yyyymmdd BETWEEN dte_effective AND dte_end;

     if (sqlca.sqlcode == 100)
     {                              /* CO15538 12/05 */
        sqlca.sqlcode = 0;
        EXEC SQL
          SELECT nvl (MAX(sak_short),0), 
                 nvl (to_number(to_char(to_date(MAX(dte_end), 'YYYYMMDD')+1, 'YYYYMMDD')), 0)
            INTO :transp_sak_short,
                 :new_start_date
            FROM t_re_transport_level
           WHERE sak_recip = :sak_recip;

        if (sqlca.sqlcode != 0)
        {
           fprintf(stderr, "ERROR: could not select max sak_short from t_re_transport_level\n");
           fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
           fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
        }

        if (insertTransportLevel(new_start_date, sak_recip, transp_sak_short) != 0)
        {
           fprintf (stderr, "ERROR: bad return from insertTransportLevel\n");
           return EXIT_FAILURE;
        }  /* 11961 */ /* 14538 - Rollback 11961 */
     }
     else
     {
        if (sqlca.sqlcode != 0)
        {
           fprintf(stderr, "ERROR: could not select from t_re_transport_level\n");
           fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
           fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
        }
     }

     return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   processXrefId()                                    */
/*                                                                      */
/*  Description:    Function is called when KAECSES txns has a cenpay_id*/
/*                  (base id) and client number(xfer id) is found in    */
/*                  t_re_base. A link is established between xfer id and*/
/*                  base id (entry added to t_re_link_rqst).            */
/*                  More information can be found in PWB/Beneficiary/   */
/*                  Link/Unlink                                         */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int processXrefId()
{

exec sql begin declare section;
int cenpay_sak_recip;
int time;
int  count = 0;
char link [1+1] = "L";
exec sql end declare section;
int cde_error = 0;
int xref_error = FALSE;

    if (memcmp(id_medicaid, base_id, sizeof(id_medicaid)) == 0)
    {
        /** Linking Beneficiary to itself !! **/
        memcpy(name_field, "CENPAY ID      ", sizeof(name_field));
        memcpy(desc_field, base_id, sizeof(11));
        cde_error = 2140;

        if (insertErrTbl(cde_error) != 0)
        {
           fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
           return EXIT_FAILURE;
        }

        num_reject++;
        return EXIT_SUCCESS;
    }

    sqlca.sqlcode = 0;
    EXEC SQL
      SELECT sak_recip
        INTO :cenpay_sak_recip
        FROM t_re_base
       WHERE id_medicaid = :base_id
         AND ind_active  = 'Y';

    if (sqlca.sqlcode == 0)
    {
       /* Check to see if the client number is in t_re_base */
       if ((checkBaseId() != 0) || (sqlca.sqlcode == 100))
       {
           if (sqlca.sqlcode == 100)     /* client no. not in t_re_base */
           {
       	       count = 0;

       	       EXEC SQL
       	       SELECT  count(*)
       	         INTO :count
       	         FROM  t_re_old_pcn
       	        WHERE  id_med_recip_prev = :id_medicaid;

       	       if (count == 0)
               {
                  memcpy(name_field, "CROSS REF ID   ", sizeof(name_field));
                  memcpy(desc_field, client_number, 11);
                  cde_error = 2002;
                  xref_error = TRUE;

                  if (insertErrTbl(cde_error) != 0)
                  {
                     fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
                     return EXIT_FAILURE;
                  }
               }
           }
           else
           {
               memcpy(name_field, "CROSS REF ID   ", sizeof(name_field));
               memcpy(desc_field, client_number, 11);
               cde_error = 2002;
               xref_error = TRUE;

               if (insertErrTbl(cde_error) != 0)
               {
                  fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
                  return EXIT_FAILURE;
               }
           }
       }
       else
       {
           count = 0;

           EXEC SQL
             SELECT  count(*)
               INTO :count
               FROM  t_re_link_rqst
              WHERE  sak_recip = :cenpay_sak_recip AND
                     sak_rcp_xref = :sak_recip;

            if (count == 0)
            {
               time = atoi(time_stamp);

               EXEC SQL
               INSERT INTO t_re_link_rqst
                           (sak_recip,
                            sak_rcp_xref,
   	                    cde_request,
                            dte_processed,
    	                    tme_stamp)
    	           VALUES  (:cenpay_sak_recip,
    	                    :sak_recip,
    	                    :link,
    	                    :curr_dte,
    	                    :time);

               if (sqlca.sqlcode != 0)
               {
    	          fprintf(stderr, "ERROR: could not insert into t_re_link_rqst\n");
    	          fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
    	          fprintf(stderr, "ERROR: sak_recip %d\n",  cenpay_sak_recip);
    	          fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
    	          return EXIT_FAILURE;
               }
            }
        }
    }
    else if (sqlca.sqlcode == 100)       /* BaseId not in t_re_base */
    {
       count = 0;

       EXEC SQL
         SELECT count(*)
           INTO :count
           FROM t_re_old_pcn
          WHERE id_med_recip_prev = :id_medicaid;

        if (count == 0)
        {
           memcpy(name_field, "CENPAY ID      ", sizeof(name_field));
           memcpy(desc_field, base_id, 12);
           cde_error = 2106;
           xref_error = TRUE;

           if (insertErrTbl(cde_error) != 0)
           {
              fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
              return EXIT_FAILURE;
           }
        }
    }
    else
    {
        fprintf(stderr,"ERROR: ProcessXrefId:could not select sak_recip from t_re_base\n");
        fprintf(stderr,"ERROR: CenpayID %s\n", base_id);
        fprintf(stderr,"ERROR: SQLCODE=%d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
    }

    sqlca.sqlcode = 0;

    if (xref_error == TRUE)
       num_reject++;
    else
       num_accept++;

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateBase()                                       */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int updateBase()
{

    sqlca.sqlcode = 0;
    EXEC SQL
    UPDATE t_re_base
    SET    dte_cred_covg_cert = :base_dte_cred_covg_cert
    WHERE sak_recip = :sak_recip;

    if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
    {
       fprintf(stderr, "ERROR: could not update t_re_base\n");
       fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
       fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
       return EXIT_FAILURE;
    }

   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   UpdateLatLong                                      */
/*                                                                      */
/*  Description:  Update latitude, longitude, ind_addr_invalid          */
/*                if the address changed.                               */
/*                                                                      */
/************************************************************************/
static int UpdateLatLong(int sakRecip)
{
      int rc = 0;
      char *lstreet = trimTrailingWhitespaces(strdup(base_adr_street1));
      char *lstreet2 = trimTrailingWhitespaces(strdup(base_adr_street2));
      char *lcity = trimTrailingWhitespaces(strdup(base_adr_city));
      char *lstate = trimTrailingWhitespaces(strdup(base_adr_state));
      char *lzip = trimTrailingWhitespaces(strdup(base_adr_zip_code));
      char *lzip4 = trimTrailingWhitespaces(strdup(base_adr_zip_code_4));

      EXEC SQL BEGIN DECLARE SECTION;
              int sqlSakRecip = sakRecip;

              char adrStreet1[30+1];
              char adrStreet2[30+1];
              char adrCity[18+1];
              char adrState[2+1];
              char adrZip[5+1];
              char adrZip4[4+1];
      EXEC SQL END DECLARE SECTION;

      EXEC SQL
              SELECT ADR_STREET_1, ADR_STREET_2, ADR_CITY, ADR_STATE, ADR_ZIP_CODE, ADR_ZIP_CODE_4
              INTO :adrStreet1, :adrStreet2, :adrCity, :adrState, :adrZip, :adrZip4
              FROM T_RE_BASE
              WHERE SAK_RECIP = :sqlSakRecip;
      ;

      if( sqlca.sqlcode != 0 )
      {
              fprintf(stderr, "ERROR: Could not get current address for sak_recip[%d]\n", sqlSakRecip);
              return 1;
      }

      trimTrailingWhitespaces(adrStreet1);
      trimTrailingWhitespaces(adrStreet2);
      trimTrailingWhitespaces(adrCity);
      trimTrailingWhitespaces(adrState);
      trimTrailingWhitespaces(adrZip);
      trimTrailingWhitespaces(adrZip4);

      if( strcmp(lstreet, adrStreet1) != 0 ||
          strcmp(lstreet2, adrStreet2) != 0 ||
          strcmp(lcity, adrCity) != 0 ||
          strcmp(lstate, adrState) != 0 ||
          strcmp(lzip, adrZip) != 0 ||
          strcmp(lzip4, adrZip4) != 0
        )
      {
          /* Latitude/Longitude trigger disabled. Get here. */
          if( calculateLatLong(base_adr_street1, base_adr_city, base_adr_state,
                               base_adr_zip_code, base_adr_zip_code_4,
                               &base_lat, &base_long, &base_geo_qual
                              ) != 0
            )
          return EXIT_FAILURE;

          EXEC SQL UPDATE T_RE_BASE
                   SET num_latitude = :base_lat,
                       num_longitude = :base_long,
                       cde_gis_quality = :base_geo_qual,
                       adr_street_1 = :base_adr_street1,
                       adr_street_2 = :base_adr_street2,
                       adr_city = :base_adr_city,
                       adr_state = :base_adr_state,
                       adr_zip_code = :base_adr_zip_code,
                       adr_zip_code_4 = :base_adr_zip_code_4,
                       ind_addr_invalid = 'N'
                   WHERE sak_recip = :sqlSakRecip
          ;

          if( sqlca.sqlcode != 0 )
          {
             fprintf(stderr, "ERROR: Updating address latitude/longitude. sqlcode: %d \n", sqlca.sqlcode);
             rc = 1;
          }
          /* Look for other indicators on the same case to update. CO 14494 */
          exec sql
          update t_re_base 
             set ind_addr_invalid = 'N'
           where sak_recip in (
              SELECT x1.sak_recip
                FROM t_re_base      b1,
                     t_re_case_xref x1, 
                     t_re_case_xref x2
               WHERE x2.sak_recip = :sqlSakRecip
                 and x1.sak_case  = x2.sak_case  
                 and x1.sak_recip = b1.sak_recip 
                 and b1.ind_addr_invalid = 'Y'  
                 and x1.dte_end > :parm_dte);

          if ((sqlca.sqlcode != 0 ) && (sqlca.sqlcode != 100))
          {
             fprintf (stderr, "ERROR: Updating address indicator by Case. sqlcode: %d \n", sqlca.sqlcode);
             rc = 1;
          }
      }

      sfree(lstreet);
      sfree(lstreet2);
      sfree(lcity);
      sfree(lstate);
      sfree(lzip);
      sfree(lzip4);

      return rc;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   processBase()                                      */
/*                                                                      */
/*  Description:    Update t_re_base demographic information            */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int processBase()
{
exec sql begin declare section;
char cde_soundex[4+1];
int sak_citizen_dsc;
int sak_cnty_off_srv;
char cde_office[1+1];
exec sql end declare section;

    sqlca.sqlcode = 0;
    EXEC SQL
      SELECT soundex(:nam_last)
      INTO   :cde_soundex
      FROM   dual;

   if (sqlca.sqlcode != 0)
   {
      fprintf(stderr, "ERROR: could not select soundex on last name\n");
      fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
      return EXIT_FAILURE;
   }

     sqlca.sqlcode = 0;
     EXEC SQL
       SELECT sak_citizen_dsc
       INTO   :sak_citizen_dsc
       FROM   t_re_citizen_dsc
       WHERE  cde_citizen_stat = :citizen;

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not select sak citizen from t_re_citizen_stat\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

     sqlca.sqlcode = 0;
     EXEC SQL
       SELECT sak_cnty_off,
              cde_office
       INTO   :sak_cnty_off_srv,
              :cde_office
       FROM   t_county_office
       WHERE  cde_county = :base_cde_county
       AND    cde_office = '1';

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not select sak cnty off from t_county_off\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      if (base_sak_cde_phone == 0)
         base_sak_cde_phone = 6;

    sqlca.sqlcode = 0;
    EXEC SQL
    UPDATE t_re_base
    SET    nam_last = :nam_last,
           nam_first = :nam_first,
           nam_mid_init = :nam_mid,
           num_ssn = :base_num_ssn,
           dte_birth = :base_dte_birth,
           cde_sex = :base_cde_sex,
           cde_ethnic = :base_cde_ethnic,
           cde_marital = ' ',
           cde_county = :base_cde_county,
           cde_office = :cde_office,
           sak_cnty_off_srv = :sak_cnty_off_srv,
           ind_mny_grant = 'N',
           cde_facility = 'N',
           ind_suspect = ' ',
           cde_ward_type = 'N',
           num_phone = :base_num_phone,
           sak_cde_phone = :base_sak_cde_phone,
           sak_citizen_dsc = :sak_citizen_dsc,
           ind_spec_hlth = :base_ind_spec_hlth,
           cde_soundex = :cde_soundex,
           ind_active = 'Y',
           cde_lang_written = :base_cde_lang_written,
           cde_lang_spoken = :base_cde_lang_spoken,
           cde_comm_media = :base_cde_comm_media,
           resp_pty_nam_last = :rp_nam_last,
           resp_pty_nam_first = :rp_nam_first,
           resp_pty_nam_mi = :rp_nam_mid,
           dte_continuous_elg = :cont_elig,
	   dte_application = to_date(:base_dte_application,'YYYYMMDD')
     WHERE sak_recip = :sak_recip
     AND   ( nam_last <> :nam_last
     OR    nam_first <> :nam_first
     OR    num_ssn <> :base_num_ssn
     OR    dte_birth <> :base_dte_birth
     OR    cde_sex <> :base_cde_sex
     OR    cde_ethnic <> :base_cde_ethnic
     OR    cde_county <> :base_cde_county
     OR    cde_office <> :base_cde_office
     OR    sak_cnty_off_srv <> :base_sak_cnty_off_srv
     OR    num_phone <> :base_num_phone
     OR    sak_citizen_dsc <> :base_sak_cde_citizen
     OR    ind_spec_hlth <> :base_ind_spec_hlth
     OR    cde_lang_written <> :base_cde_lang_written
     OR    cde_lang_spoken <> :base_cde_lang_spoken
     OR    cde_comm_media <> :base_cde_comm_media
     OR    resp_pty_nam_last <> :rp_nam_last
     OR    resp_pty_nam_first <> :rp_nam_first
     OR    dte_continuous_elg <> :cont_elig
     OR    dte_application  <> to_date(:base_dte_application,'YYYYMMDD'));

     if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
     {
        fprintf(stderr, "ERROR: could not update t_re_base\n");
        fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }
     else if( UpdateLatLong(sak_recip) != 0 )
        return EXIT_FAILURE;

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertRace()                                       */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int insertRace()
{
exec sql begin declare section;
int sak_short = 0;
int tmp_sak_short = 0;
char space = ' ';
char code[1+1];
exec sql end declare section;
int i = 0;

      sqlca.sqlcode = 0;
      EXEC SQL
         SELECT nvl(max(sak_short),0)
         INTO   :sak_short
         FROM   t_re_race_xref
         WHERE  sak_recip = :new_sak_recip;

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not select max sak_short from t_re_race_xref\n");
         fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }
      else
      {
         for(i=0; (race_code[i] != NULL); i++)
         {
           if (race_code[i] != space)
           {
            code[0] = race_code[i];
            code[1]= '\0';

            sqlca.sqlcode = 0;
            EXEC SQL
              SELECT sak_short
              INTO   :tmp_sak_short
              FROM   t_re_race_xref
              WHERE  sak_recip = :new_sak_recip
              AND    cde_race  = :code;

            if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
            {
               fprintf(stderr, "ERROR: could not select race code\n");
               fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
               fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
               return EXIT_FAILURE;
            }
            else if (sqlca.sqlcode == 100)
            {
               sak_short++;

               sqlca.sqlcode = 0;
               EXEC SQL
                 INSERT INTO t_re_race_xref
                            (sak_recip,
                             cde_race,
                             sak_short)
                 VALUES  (:new_sak_recip,
                          :code,
                          :sak_short);

              if (sqlca.sqlcode != 0)
              {
                 fprintf(stderr, "ERROR: could not insert race code\n");
                 fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
                 fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
                 return EXIT_FAILURE;
              }
           }
          }
         }/*End for loop*/
       }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   popCode()                                          */
/*                                                                      */
/*  Description:     Assigns population code                            */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO8831   02/28/2006 Srinivas D       Added PDI logic                */
/************************************************************************/
static int popCode()
{

int cde_error;

    if (getPopCodes(&pop_code,
                    &priority_code,
                    &man_code,
                    &cap_ind,
                    legal_status,
                    citizen,
                    cash_prog,
                    cash_prog_sb,
                    src_fund,
                    med_elig_ind,
                    med_subtype,
                    prog_type,
                    prog_subtype,
                    qmb_ind,
                    epsdt_age,
                    pres_dis_ind) != 0)
    {
       fprintf (stderr, "ERROR: bad return from getPopCodes\n");
       return EXIT_FAILURE;
    }

    if (pop_code[0] == '0')
    {
        memcpy(name_field, "SAK_CDE_AID    ", sizeof(name_field));
        memcpy(desc_field, "INVALID ASSIGN ", sizeof(desc_field));
        cde_error = 2004;

        end_process = TRUE;

        if (insertErrTbl(cde_error) != 0)
        {
           fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
           return EXIT_FAILURE;
        }

        return EXIT_SUCCESS;
     }

     return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   benefitPlan()                                      */
/*                                                                      */
/*  Description:    Assigns benefit plans                               */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO8831   02/28/2006 Srinivas D       Added PDI logic                */
/************************************************************************/
static int benefitPlan()
{

int flag = FALSE;
int cde_error = 0;

    ksc_num_rank = 0;
    ksc_2nd_num_rank = 0;

    elig_bp_ptr = (ELIG_BENEFIT_PLAN_S *) &elig_bp;

    if ( determine_benefit_plan (med_elig_ind,
                                 part_code,
                                 qmb_ind,
                                 cash_prog,
                                 char_dte_birth,
                                 char_curr_dte,
                                 citizen,
                                 med_subtype,
                                 placement,
                                 prog_subtype,
                                 cde_lime,
                                 prog_type,
                                 pres_dis_ind,
                                 elig_bp_ptr) != 1)
    {
        memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
        memcpy(desc_field, "INVALID ASSIGN ", sizeof(desc_field));
        cde_error = 2007;

        end_process = TRUE;

        if (insertErrTbl(cde_error) != 0)
        {
           fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
           return EXIT_FAILURE;
        }

        return EXIT_SUCCESS;
    }
    else if (elig_bp_ptr->sak_pub_hlth == -1)
    {
        memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
        memcpy(desc_field, "NOT APPLICABLE ", sizeof(desc_field));
        cde_error = 2009;

        end_process = TRUE;

        if (insertErrTbl(cde_error) != 0)
        {
           fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
           return EXIT_FAILURE;
        }

        return EXIT_SUCCESS;
    }
    else
    {
       elig_bp_ptr = (ELIG_BENEFIT_PLAN_S *) &elig_bp;

       while (elig_bp_ptr->cde_pgm_health[0] != '\0')
       {
          if (memcmp(elig_bp_ptr->cde_pgm_health, "MN   ", sizeof(elig_bp_ptr->cde_pgm_health)) == 0)
          {
             if (checkSpndwn(&flag) != 0)
             {
                fprintf (stderr, "ERROR: bad return from checkSpndwn\n");
                return EXIT_FAILURE;
             }

             if (flag == FALSE)
                elig_bp_ptr->num_rank = 10;
          }
          elig_bp_ptr++;
       }

       elig_bp_ptr = (ELIG_BENEFIT_PLAN_S *) &elig_bp;
       ksc_num_rank = elig_bp_ptr->num_rank;         /* first BP rank */

       if (elig_bp[1].sak_pub_hlth != 0)
       {
          ksc_2nd_num_rank = elig_bp[1].num_rank;     /* second BP rank */
       }
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   hierarchy()                                        */
/*                                                                      */
/*  Description:    Multiple KAECSES records can come in for the same   */
/*                  beneficiary. Apply the hierarchy to determine which */
/*                  record takes precedence (winning record), and which */
/*                  does not (losing record). Report the losing elig.   */
/*                  record on the error report.                         */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO9489   05/26/2006 Srinivas D       Modified to include addition   */
/*                                       benefit plan rules for pres    */
/*                                       eligibility                    */ 
/************************************************************************/
static int hierarchy(int link_cnt)
{

int i;
int hold_code = 0;
int hold_num_rank = 0;
int hold_2nd_num_rank = 0;
int cde_error = 0;
int good_record = FALSE;
ELIG_REC *buff;
char hold_pop_code[2+1];
char hold_ksc_case_number[10+1];
char hold_worker[6+1];
char hold_dte_time_stamp[14+1];

   currptr = headptr;
   prem_case_change = 'n';
   strncpy (prem_case_num_prev, "         ", sizeof (prem_case_num_prev));

   memset(sak_pub_hlth, 0x00, sizeof(sak_pub_hlth));
   memset(num_rank, 0x00, sizeof(num_rank));

   while (link_cnt != 0)
   {
      tempptr = currptr->next;

      copyBftFields();

     sqlca.sqlcode = 0;
       EXEC SQL
       SELECT trunc(months_between(SYSDATE, (to_date(:base_dte_birth, 'YYYYMMDD')))/12)
       INTO   :epsdt_age
       FROM   dual;

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr,"ERROR: could not calculate age\n");
         return EXIT_FAILURE;
      }

      if (popCode() != 0)
      {
         fprintf (stderr, "ERROR: bad return from popCode\n");
         return EXIT_FAILURE;
      }

      if (end_process == TRUE)
      {
         num_reject++;
         link_cnt --;
         currptr = tempptr;
         end_process = FALSE;
         continue;
      }

      if (benefitPlan() != 0)
      {
         fprintf (stderr, "ERROR: bad return from benefitPlan\n");
         return EXIT_FAILURE;
      }

      if (end_process == TRUE)
      {
         num_reject++;
         link_cnt --;
         currptr = tempptr;
         end_process = FALSE;
         continue;
      }
      else
      {
         if (good_record == FALSE)
            good_record = TRUE;
      }

      if (hold_num_rank > 0)
      {
        if ( ((hold_num_rank == p21_num_rank) || (hold_num_rank == p19_num_rank)) &&
              ((ksc_num_rank != p21_num_rank)  && (ksc_num_rank != p19_num_rank)) )    /* PE BP with OTHER BP on file */
        {
            /* hold record is PE record, so Discard hold record */
            buff = currptr;
            hold_num_rank = ksc_num_rank;
            hold_2nd_num_rank = ksc_2nd_num_rank;
            hold_code = priority_code;
            curr_priority_code = priority_code;
 
            memcpy(ksc_case_number, hold_ksc_case_number, sizeof(ksc_case_number));
            memcpy(worker, hold_worker, sizeof(worker));
            memcpy(dte_time_stamp, hold_dte_time_stamp, sizeof(dte_time_stamp));
            memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
            memcpy(desc_field, "OT ON FILE     ", sizeof(desc_field));
            bLowerElig = TRUE;
            cde_error = 2162;
            num_reject++;

            if (insertErrTbl(cde_error) != 0)
            {
               fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
               return EXIT_FAILURE;
            }
  
            if (insertTmpPeRevTxn(ksc_case_number,
                               benefit_month_yyyymm,
                               sak_pub_hlth[0],
                               benefit_month_yyyymmdd,
                               "N",
                               "2167") != 0)
            {
               fprintf (stderr, "ERROR: bad return from insertTmpPeRevTxn for OT ON FILE\n");
               return EXIT_FAILURE;
            }
                                
            elig_bp_ptr = (ELIG_BENEFIT_PLAN_S *) &elig_bp;
            i=0;

            while (elig_bp_ptr->sak_pub_hlth != 0)
            {
               sak_pub_hlth[i] = elig_bp_ptr->sak_pub_hlth;
               num_rank[i] = elig_bp_ptr->num_rank;
               i++;
               elig_bp_ptr++;
            }  
        }
        else if ( ((hold_num_rank != p21_num_rank) && (hold_num_rank != p19_num_rank)) &&
                  ((ksc_num_rank == p21_num_rank)  || (ksc_num_rank == p19_num_rank)) )    /* PE BP with OTHER BP on file */ 
        {   
              /**  current record is a PE record, so Discard current record  **/
               memcpy(pop_code, hold_pop_code, sizeof(hold_pop_code));

               memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
               memcpy(desc_field, "OT ON FILE     ", sizeof(desc_field));
               bLowerElig = TRUE;
               cde_error = 2162;
               num_reject++;

               if (insertErrTbl(cde_error) != 0)
               {
                  fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
                  return EXIT_FAILURE;
               }
               if (insertTmpPeRevTxn(ksc_case_number,
                                  benefit_month_yyyymm,
                                  elig_bp_ptr->sak_pub_hlth,
                                  benefit_month_yyyymmdd,
                                  "N",
                                  "2167") != 0)
               {
                  fprintf (stderr, "ERROR: bad return from insertTmpPeRevTxn for OT ON FILE\n");
                  return EXIT_FAILURE;
               }  
        }
        else
        {
         if (ksc_num_rank > hold_num_rank)
         {
            memcpy(ksc_case_number, hold_ksc_case_number, sizeof(ksc_case_number));
            memcpy(worker, hold_worker, sizeof(worker));
            memcpy(dte_time_stamp, hold_dte_time_stamp, sizeof(dte_time_stamp));

            memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
            memcpy(desc_field, "LOWER ELIG     ", sizeof(desc_field));
            bLowerElig = TRUE;
            cde_error = 2010;
            num_reject++;

            if ((ksc_num_rank == p19_num_rank) && (hold_num_rank == p21_num_rank))
            {
               /* p19 rank is better than p21 so hold record is p21 and discard it */ 
                memcpy(desc_field, "P19 ON FILE    ", sizeof(desc_field));
                cde_error = 2163;

                if (insertTmpPeRevTxn(ksc_case_number,
                                  benefit_month_yyyymm,
                                  sak_pub_hlth[0],
                                  benefit_month_yyyymmdd,
                                  "N",
                                  "2168") != 0)
                {
                  fprintf (stderr, "ERROR: bad return from insertTmpPeRevTxn for P19 ON FILE\n");
                  return EXIT_FAILURE;
                }  
            }

            if (insertErrTbl(cde_error) != 0)
            {
               fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
               return EXIT_FAILURE;
            }

            buff = currptr;
            hold_num_rank = ksc_num_rank;
            hold_2nd_num_rank = ksc_2nd_num_rank;
            hold_code = priority_code;
            curr_priority_code = priority_code;

            elig_bp_ptr = (ELIG_BENEFIT_PLAN_S *) &elig_bp;
            i=0;

            while (elig_bp_ptr->sak_pub_hlth != 0)
            {
               sak_pub_hlth[i] = elig_bp_ptr->sak_pub_hlth;
               num_rank[i] = elig_bp_ptr->num_rank;
               i++;
               elig_bp_ptr++;
            }
          }
          else if (ksc_num_rank == hold_num_rank)
          {
             if (ksc_2nd_num_rank > hold_2nd_num_rank)
             {
            	/**  Discard hold record  **/
            	buff = currptr;
	        	hold_2nd_num_rank = ksc_2nd_num_rank;
            	hold_code = priority_code;
            	curr_priority_code = priority_code;

            	memcpy(ksc_case_number, hold_ksc_case_number, sizeof(ksc_case_number));
            	memcpy(worker, hold_worker, sizeof(worker));
            	memcpy(dte_time_stamp, hold_dte_time_stamp, sizeof(dte_time_stamp));

            	memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
            	memcpy(desc_field, "LOWER ELIG     ", sizeof(desc_field));
                bLowerElig = TRUE;
            	cde_error = 2010;
                num_reject++;

            	if (insertErrTbl(cde_error) != 0)
            	{
            	   fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
            	   return EXIT_FAILURE;
            	}

            	elig_bp_ptr = (ELIG_BENEFIT_PLAN_S *) &elig_bp;
            	i=0;

            	while (elig_bp_ptr->sak_pub_hlth != 0)
            	{
            	   sak_pub_hlth[i] = elig_bp_ptr->sak_pub_hlth;
            	   num_rank[i] = elig_bp_ptr->num_rank;
            	   i++;
            	   elig_bp_ptr++;
            	}
             }
             else if (ksc_2nd_num_rank < hold_2nd_num_rank)
             {
                /**  Discard current record  **/
            	memcpy(pop_code, hold_pop_code, sizeof(hold_pop_code));

            	memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
            	memcpy(desc_field, "LOWER ELIG     ", sizeof(desc_field));
                bLowerElig = TRUE;
            	cde_error = 2010;
                num_reject++;

            	if (insertErrTbl(cde_error) != 0)
            	{
            	   fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
            	   return EXIT_FAILURE;
            	}
             }
             else
             {
                /** ksc_2nd_num_rank == hold_2nd_num_rank, det. by pop_code / TimeStamp **/
            	if (priority_code < hold_code)
            	{
            	   buff = currptr;
            	   hold_code = priority_code;
            	   curr_priority_code = priority_code;

            	   memcpy(ksc_case_number, hold_ksc_case_number, sizeof(ksc_case_number));
            	   memcpy(worker, hold_worker, sizeof(worker));
            	   memcpy(dte_time_stamp, hold_dte_time_stamp, sizeof(dte_time_stamp));

            	   memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
            	   memcpy(desc_field, "LOWER ELIG     ", sizeof(desc_field));
                   bLowerElig = TRUE;
            	   cde_error = 2010;
                   num_reject++;

            	   if (insertErrTbl(cde_error) != 0)
            	   {
            	      fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
            	      return EXIT_FAILURE;
            	   }

            	   elig_bp_ptr = (ELIG_BENEFIT_PLAN_S *) &elig_bp;
            	   i=0;

            	   while (elig_bp_ptr->sak_pub_hlth != 0)
            	   {
            	      sak_pub_hlth[i] = elig_bp_ptr->sak_pub_hlth;
            	      num_rank[i] = elig_bp_ptr->num_rank;
            	      i++;
            	      elig_bp_ptr++;
            	   }
            	}
            	else if (priority_code == hold_code)
            	{
            	   if (memcmp(dte_time_stamp, hold_dte_time_stamp, sizeof(dte_time_stamp)) > 0)
            	   {
            	      buff = currptr;
            	      memcpy(pop_code, hold_pop_code, sizeof(hold_pop_code));
            	      curr_priority_code = priority_code;
            	      memcpy(dte_time_stamp, hold_dte_time_stamp, sizeof(dte_time_stamp));

            	      memcpy(ksc_case_number, hold_ksc_case_number, sizeof(ksc_case_number));
            	      memcpy(worker, hold_worker, sizeof(worker));
            	      memcpy(dte_time_stamp, hold_dte_time_stamp, sizeof(dte_time_stamp));

            	      memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
            	      memcpy(desc_field, "DUPLICATE      ", sizeof(desc_field));
            	      cde_error = 2000;
                      num_reject++;

            	      if (insertErrTbl(cde_error) != 0)
            	      {
            	         fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
            	         return EXIT_FAILURE;
            	      }

            	      elig_bp_ptr = (ELIG_BENEFIT_PLAN_S *) &elig_bp;
            	      i=0;

            	      while (elig_bp_ptr->sak_pub_hlth != 0)
            	      {
            	         sak_pub_hlth[i] = elig_bp_ptr->sak_pub_hlth;
            	         num_rank[i] = elig_bp_ptr->num_rank;
            	         i++;
            	         elig_bp_ptr++;
            	      }
            	   }
            	   else if (memcmp(dte_time_stamp, hold_dte_time_stamp, sizeof(dte_time_stamp)) == 0)
            	   {
            	      curr_priority_code = hold_code;

            	      memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
            	      memcpy(desc_field, "EQUAL ELIG     ", sizeof(desc_field));
            	      cde_error = 2010;
                      bEqualElig = TRUE;
                      num_reject++;

            	      if (insertErrTbl(cde_error) != 0)
            	      {
            	         fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
            	         return EXIT_FAILURE;
            	      }
            	   }
            	   else
            	   {
            	      curr_priority_code = hold_code;

            	      memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
            	      memcpy(desc_field, "LOWER ELIG     ", sizeof(desc_field));
                      bLowerElig = TRUE;
            	      cde_error = 2010;
                      num_reject++;

            	      if (insertErrTbl(cde_error) != 0)
            	      {
            	         fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
            	         return EXIT_FAILURE;
            	      }
            	   }
            	}
            	else     /**** priority_code > hold_code ****/
            	{
            	   memcpy(pop_code, hold_pop_code, sizeof(hold_pop_code));

            	   memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
            	   memcpy(desc_field, "LOWER ELIG     ", sizeof(desc_field));
                   bLowerElig = TRUE;
            	   cde_error = 2010;
                   num_reject++;

            	   if (insertErrTbl(cde_error) != 0)
            	   {
            	      fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
            	      return EXIT_FAILURE;
            	   }
            	}
             }
          }
          else      /**** ksc_num_rank < hold_num_rank ****/
          {
             memcpy(pop_code, hold_pop_code, sizeof(hold_pop_code));

             memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
             memcpy(desc_field, "LOWER ELIG     ", sizeof(desc_field));
             bLowerElig = TRUE;
             cde_error = 2010;
             num_reject++;
          
             if ((ksc_num_rank == p21_num_rank) && (hold_num_rank == p19_num_rank))
             {
               /* p21 rank is lower than p19 so current record is p21 and discard it */
                memcpy(desc_field, "P19 ON FILE    ", sizeof(desc_field));
                cde_error = 2163;

                if (insertTmpPeRevTxn(ksc_case_number,
                                  benefit_month_yyyymm,
                                  elig_bp_ptr->sak_pub_hlth,
                                  benefit_month_yyyymmdd,
                                  "N",
                                  "2168") != 0)
                {
                  fprintf (stderr, "ERROR: bad return from insertTmpPeRevTxn for P19 ON FILE\n");
                  return EXIT_FAILURE;
                }
             }

             if (insertErrTbl(cde_error) != 0)
             {
                fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
                return EXIT_FAILURE;
             }
          }
        }
      }
      else        /**** hold_num_rank == 0 ****/
      {
         curr_priority_code = priority_code;
         memcpy(hold_pop_code, pop_code, sizeof(hold_pop_code));
         hold_num_rank = ksc_num_rank;
	 hold_2nd_num_rank = ksc_2nd_num_rank;
         hold_code = priority_code;

         memcpy(hold_dte_time_stamp, dte_time_stamp, sizeof(hold_dte_time_stamp));
         memcpy(hold_ksc_case_number, ksc_case_number, sizeof(hold_ksc_case_number));
         memcpy(hold_worker, worker, sizeof(hold_worker));
         memcpy(hold_dte_time_stamp, dte_time_stamp, sizeof(hold_dte_time_stamp));

         buff = currptr;

         elig_bp_ptr = (ELIG_BENEFIT_PLAN_S *) &elig_bp;
         i=0;

         while (elig_bp_ptr->sak_pub_hlth != 0)
         {
             sak_pub_hlth[i] = elig_bp_ptr->sak_pub_hlth;
             num_rank[i] = elig_bp_ptr->num_rank;
             i++;
             elig_bp_ptr++;
         }
      }

      link_cnt --;
      currptr = tempptr;

   }/*End Of while loop*/

    if (good_record == TRUE)
       end_process = FALSE;
    else
    {
       end_process = TRUE;
       return EXIT_SUCCESS;
    }

    currptr = buff;                 /* restore the highest ranked record */
    bene_num_rank = hold_num_rank;
    ksc_num_rank  = hold_num_rank;
    ksc_2nd_num_rank = hold_2nd_num_rank;
    copyBftFields();

    sqlca.sqlcode = 0;              /* def. 3369 */
       EXEC SQL
       SELECT trunc(months_between(SYSDATE, (to_date(:base_dte_birth, 'YYYYMMDD')))/12)
       INTO   :epsdt_age
       FROM   dual;

    if (sqlca.sqlcode != 0)
    {
       fprintf(stderr,"ERROR: could not calculate age\n");
       return EXIT_FAILURE;
    }

    if (popCode() != 0)
    {
       fprintf (stderr, "ERROR: bad return from popCode\n");
       return EXIT_FAILURE;
    }

    if (end_process == TRUE)
    {
       num_reject++;
       return EXIT_SUCCESS;
    }

    if (calBenefitMth() != 0)
    {
       fprintf (stderr, "ERROR: bad return from calBenefitMth\n");
       return EXIT_FAILURE;
    }

    id_card_benefit_end = benefit_month_lastday;

    if ( (sak_pub_hlth[0] == 4) &&
         ((benefit_month_yyyymm < curr_dte_yyyymm) ||
         ((benefit_month_lastday == curr_dte) && (curr_dte == curr_lastday)) ) )
    {
       if (retro_t21[0] == ' ')
       {
          memcpy(name_field, "T21 RETRO IND  ", sizeof(name_field));
          memcpy(desc_field, "IND NOT SET    ", sizeof(desc_field));
          cde_error = 2011;
          num_reject++;
          end_process = TRUE;

          if (insertErrTbl(cde_error) != 0)
          {
             fprintf (stderr, "ERROR: bad return from insertErrTbl\n");
             return EXIT_FAILURE;
          }
        }
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertElig()                                       */
/*                                                                      */
/*  Description:     Insert new bene eligibility.                       */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int insertElig(int tmp_case)
{

int i = 0;
exec sql begin declare section;
int lSakPubHlth;
char cIndCredCovgCert;
exec sql end declare section;
int bCredCovgCert = FALSE;

    while (sak_pub_hlth[i] != 0)
    {
      /* Only insert record if valid spenddown OR invalid spenddown but */
      /* eligibility is not a spenddown health program (QMB and MN, QMB */
      /* record would be inserted but invalid MN would not).            */
      if (bValidSpendRec  == TRUE  ||
         (bValidSpendRec  == FALSE      &&
          sak_pub_hlth[i] != HLTH_MN    &&
          sak_pub_hlth[i] != HLTH_SOBRA &&
          sak_pub_hlth[i] != HLTH_INMAT))
      {
        if (bCredCovgCert == FALSE)
        {
          lSakPubHlth = sak_pub_hlth[i];

          sqlca.sqlcode = 0;
          EXEC SQL
             SELECT ind_cred_covg_cert
             INTO   :cIndCredCovgCert
             FROM   t_pub_hlth_pgm
             WHERE  sak_pub_hlth = :lSakPubHlth;

          if (sqlca.sqlcode != 0)
          {
              fprintf (stderr, "ERROR: unable to retrieve ind_cred_cov_cert from t_pub_hlth_pgm\n");
              return EXIT_FAILURE;
          }
          else if (cIndCredCovgCert == 'Y')
               bCredCovgCert = TRUE;
        }

        if (insertEligSeg(sak_pub_hlth[i], num_rank[i]) != 0)
        {
            fprintf (stderr, "ERROR: bad return from insertEligSeg\n");
            return EXIT_FAILURE;
        }

        if (getSakPopCode() != 0)
        {
           fprintf (stderr, "ERROR: bad return from getSakPopCode\n");
           return EXIT_FAILURE;
        }

       if (insertAidEligSeg(tmp_case) != 0)
       {
          fprintf (stderr, "ERROR: bad return from insertAidEligSeg\n");
          return EXIT_FAILURE;
       }

       bAddedRecord = TRUE;
      }  /* end if */
      i++;
    }/*End of while loop*/

  if (bAddedRecord == TRUE)
  {
    if (memcmp(prog_type, "MP", sizeof(prog_type)) == 0)
    {
       if (povPercent() != 0)
       {
          fprintf (stderr, "ERROR: bad return from povPercent\n");
          return EXIT_FAILURE;
       }
    }

    if (insertIDCard(elig_sak_recip) != 0)
    {
       fprintf (stderr, "ERROR: bad return from insertIDCard\n");
       return EXIT_FAILURE;
    }

    if (bCredCovgCert == TRUE)
    {
       if (updateBase() != 0)
       {
          fprintf(stderr, "ERROR: bad return from updateBase\n");
          return EXIT_FAILURE;
       }
    }

  }
  return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertOneElig()                                    */
/*                                                                      */
/*  Description:    inserts elig recs for one sak_pub_hlth.             */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   09/13/2003 James Mercer     Initial Release                */
/************************************************************************/
static int insertOneElig(int in_index)
{

     if (insertEligSeg(sak_pub_hlth[in_index], num_rank[in_index]) != 0)
     {
         fprintf (stderr, "ERROR: bad return from insertEligSeg\n");
         return EXIT_FAILURE;
     }

     if (getSakPopCode() != 0)
     {
        fprintf (stderr, "ERROR: bad return from getSakPopCode\n");
        return EXIT_FAILURE;
     }

    if (insertAidEligSeg(benefit_case) != 0)
    {
       fprintf (stderr, "ERROR: bad return from insertAidEligSeg\n");
       return EXIT_FAILURE;
    }

    if (memcmp(prog_type, "MP", sizeof(prog_type)) == 0)
    {
       if (povPercent() != 0)
       {
          fprintf (stderr, "ERROR: bad return from povPercent\n");
          return EXIT_FAILURE;
       }
    }

    bAddedRecord = TRUE;

    if (insertIDCard(elig_sak_recip) != 0)
    {
       fprintf (stderr, "ERROR: bad return from insertIDCard\n");
       return EXIT_FAILURE;
    }

     if (elig_covg_cert[in_index][0] == 'Y')
     {
        if (updateBase() != 0)
        {
           fprintf(stderr, "ERROR: bad return from updateBase\n");
           return EXIT_FAILURE;
        }
     }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   copyElig()                                         */
/*                                                                      */
/*  Description:    Insert Elig record with values from existing elig   */
/*                  record.                                             */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   09/03/2003 James Mercer      Initial Release               */
/************************************************************************/
static int copyElig(int in_sak_pub_hlth, int in_num_rank, int in_aid_index)
{

    if (insertEligSeg(in_sak_pub_hlth, in_num_rank) != 0)
    {
        fprintf (stderr, "ERROR: bad return from insertEligSeg\n");
        return EXIT_FAILURE;
    }

    /*copy all aid eligs (within date range)*/
    if (copyAidEligs(in_aid_index) != 0)
    {
       fprintf(stderr, "ERROR: bad return from copyAidEligs\n");
       return EXIT_FAILURE;
    }

    if (memcmp(prog_type, "MP", sizeof(prog_type)) == 0)
    {
       if (povPercent() != 0)
       {
          fprintf (stderr, "ERROR: bad return from povPercent\n");
          return EXIT_FAILURE;
       }
    }

    bAddedRecord = TRUE;

    if (insertIDCard(sak_recip) != 0)
    {
       fprintf (stderr, "ERROR: bad return from insertIDCard\n");
       return EXIT_FAILURE;
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   getSakPopCode()                                    */
/*                                                                      */
/*  Description:     Get SAK for the population code                    */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int getSakPopCode()
{

      sqlca.sqlcode = 0;
      EXEC SQL
         SELECT sak_cde_aid
         INTO   :sak_cde_aid
         FROM   t_cde_aid
         WHERE  cde_aid_category = :pop_code;

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not select sak cde aid from t_cde_aid\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertEligSeg()                                    */
/*                                                                      */
/*  Description:    Insert new bene eligibility to t_re_elig            */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int insertEligSeg(int sak, int rank)
{

exec sql begin declare section;
int open_date = OPEN_DATE;
int sak_pub2 = 0;
int numrank = 0;
exec sql end declare section;

          case_dte_cert = benefit_month_yyyymmdd;
          sak_pub2 = sak;
          numrank = rank;

          sqlca.sqlcode = 0;
          EXEC SQL
            SELECT nvl(max(sak_pgm_elig),0)
            INTO   :new_sak_pgm_elig
            FROM t_re_elig
            WHERE sak_recip = :elig_sak_recip;

          new_sak_pgm_elig++;

          if (sqlca.sqlcode != 0)
          {
             fprintf(stderr, "ERROR: could not select max sak from t_re_elig\n");
             fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
             fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
             return EXIT_FAILURE;
          }

          sqlca.sqlcode = 0;
          EXEC SQL
            INSERT INTO t_re_elig
                   (sak_recip,
                    sak_pgm_elig,
                    sak_pub_hlth,
                    dte_effective,
                    dte_end,
                    cde_pre_entry_stat,
                    sak_elig_stop,
                    cde_status1,
                    dte_created,
                    dte_last_updated,
                    dte_active_thru,
                    num_rank)
            VALUES (:elig_sak_recip,
                    :new_sak_pgm_elig,
                    :sak_pub2,
                    :benefit_month_yyyymmdd,
                    :benefit_month_lastday,
                    ' ',
                    '0',
                    ' ',
                    :curr_dte,
                    :curr_dte,
                    :open_date,
                    :numrank);

           if (sqlca.sqlcode != 0)
           {
              fprintf(stderr, "ERROR: could not insert into t_re_elig\n");
              fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
              fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
              return EXIT_FAILURE;
           }

           return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertAidEligSeg()                                 */
/*                                                                      */
/*  Description:    Insert new bene eligible for Medicaid coverage in   */
/*                  an aid category                                     */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO6816   09/08/2004 Lisa Salgat      Added tmp_case to logic        */
/*  CO8831   02/28/2006 Srinivas D       Added PDI field to INSERT      */
/************************************************************************/
static int insertAidEligSeg(int tmp_case)
{

int i = 0;
exec sql begin declare section;
int open_date = OPEN_DATE;
char space[1+1]=" ";
exec sql end declare section;

    case_index = 0;
    if (compareAidEligCase(tmp_case) != 0)
    {
       fprintf (stderr, "ERROR: bad return from compareAidEligCase\n");
       return EXIT_FAILURE;
    }

    for (i=0; i <= case_index; i++)
    {
       if (getNewSakAidElig() != 0)
       {
          fprintf (stderr, "ERROR: bad return from getNewSakAidElig\n");
          return EXIT_FAILURE;
       }

          space[1]='\0';

          sqlca.sqlcode = 0;
          EXEC SQL
            INSERT INTO t_re_aid_elig
                   (sak_aid_elig,
                    sak_recip,
                    sak_pgm_elig,
                    sak_cde_aid,
                    sak_case,
                    dte_effective,
                    dte_end,
                    cde_status1,
                    kes_cde_med_type,
                    kes_cde_med_sub,
                    kes_cde_inv_med_sb,
                    kes_cde_med_elig,
                    kes_cde_cash_type,
                    kes_cde_cash_sub,
                    kes_cde_fund_src,
                    kes_cde_typ_place,
                    kes_cde_legal_stat,
                    kes_ind_qmb,
                    kes_cde_partic,
                    kes_cde_lime,
                    kes_cde_lime_title,
                    kes_cde_pdi,
                    dte_created,
                    dte_last_updated,
                    dte_active_thru)
            VALUES (:new_sak_aid_elig,
                    :elig_sak_recip,
                    :new_sak_pgm_elig,
                    :sak_cde_aid,
                    :aid_elig_case[i],
                    :aid_elig_start[i],
                    :aid_elig_end[i],
                    :space,
                    :prog_type,
                    :prog_subtype,
                    :med_subtype,
                    :med_elig_ind,
                    :cash_prog,
                    :cash_prog_sb,
                    :src_fund,
                    :placement,
                    :legal_status,
                    :qmb_ind,
                    :part_code,
                    ' ',
                    ' ',
                    :pres_dis_ind,
                    :curr_dte,
                    :curr_dte,
                    :open_date);

           if (sqlca.sqlcode != 0)
           {
              fprintf(stderr, "ERROR: could not insert into t_re_aid_elig\n");
              fprintf(stderr,"ERROR: ID Medicaid%s\n", id_medicaid);
              fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
              return EXIT_FAILURE;
           }
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   copyAidEligSeg()                                   */
/*                                                                      */
/*  Description:    Insert Elig Aid record with values from existing    */
/*                  elig aid record.                                    */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   09/03/2003 James Mercer      Initial Release               */
/*  CO8831   02/28/2006 Srinivas D        Added pdi filed to INSERT     */
/************************************************************************/
static int copyAidEligSeg(int in_aid_index)
{
int i = 0;
exec sql begin declare section;
int open_date = OPEN_DATE;
char space[1+1]=" ";
exec sql end declare section;

    case_index = 0;
    if (compareAidEligCase(elig_sak_case[in_aid_index]) != 0)
    {
       fprintf (stderr, "ERROR: bad return from compareAidEligCase\n");
       return EXIT_FAILURE;
    }

    for (i=0; i <= case_index; i++)
    {
       if (getNewSakAidElig() != 0)
       {
          fprintf (stderr, "ERROR: bad return from getNewSakAidElig\n");
          return EXIT_FAILURE;
       }

          space[1]='\0';

          sqlca.sqlcode = 0;
          EXEC SQL
            INSERT INTO t_re_aid_elig
                   (sak_aid_elig,
                    sak_recip,
                    sak_pgm_elig,
                    sak_cde_aid,
                    sak_case,
                    dte_effective,
                    dte_end,
                    cde_status1,
                    kes_cde_med_type,
                    kes_cde_med_sub,
                    kes_cde_inv_med_sb,
                    kes_cde_med_elig,
                    kes_cde_cash_type,
                    kes_cde_cash_sub,
                    kes_cde_fund_src,
                    kes_cde_typ_place,
                    kes_cde_legal_stat,
                    kes_ind_qmb,
                    kes_cde_partic,
                    kes_cde_lime,
                    kes_cde_lime_title,
                    kes_cde_pdi,
                    dte_created,
                    dte_last_updated,
                    dte_active_thru)
            VALUES (:new_sak_aid_elig,
                    :elig_sak_recip,
                    :new_sak_pgm_elig,
                    :elig_sak_cde_aid[in_aid_index],
                    :aid_elig_case[i],
                    :aid_elig_start[i],
                    :aid_elig_end[i],
                    :space,
                    :elig_prog_type[in_aid_index],
                    :elig_prog_subtype[in_aid_index],
                    :elig_med_subtype[in_aid_index],
                    :elig_med_elig_ind[in_aid_index],
                    :elig_cash_prog[in_aid_index],
                    :elig_cash_prog_sb[in_aid_index],
                    :elig_src_fund[in_aid_index],
                    :elig_typ_place[in_aid_index],
                    :elig_legal_status[in_aid_index],
                    :elig_qmb_ind[in_aid_index],
                    :elig_partic[in_aid_index],
                    ' ',
                    ' ',
                    :elig_pres_dis_ind[in_aid_index],
                    :curr_dte,
                    :curr_dte,
                    :open_date);

           if (sqlca.sqlcode != 0)
           {
              fprintf(stderr, "ERROR: could not insert into t_re_aid_elig\n");
              fprintf(stderr, "ERROR: ID Medicaid%s\n", id_medicaid);
              fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
              return EXIT_FAILURE;
           }
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   historyElig()                                      */
/*                                                                      */
/*  Description:    Inactivate bene's elig. and aid elig segments       */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int historyElig(int sak)
{

exec sql begin declare section;
int sak_pgm_elig;
exec sql end declare section;

          sak_pgm_elig = sak;

          sqlca.sqlcode = 0;
          EXEC SQL
          UPDATE t_re_elig
          SET cde_status1 = 'H',
              dte_last_updated = :curr_dte,
              dte_active_thru = :curr_dte
          WHERE sak_recip = :sak_recip and sak_pgm_elig = :sak_pgm_elig;

          if (sqlca.sqlcode != 0)
          {
             fprintf(stderr, "ERROR: could not history off t_re_elig\n");
             fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
             fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
             return EXIT_FAILURE;
          }

          sqlca.sqlcode = 0;
          EXEC SQL
            UPDATE t_re_aid_elig
            SET cde_status1 = 'H',
                dte_last_updated = :curr_dte,
                dte_active_thru  = :curr_dte
            WHERE sak_recip    = :sak_recip 
              AND sak_pgm_elig = :sak_pgm_elig;

           if (sqlca.sqlcode != 0)
           {
              fprintf(stderr, "ERROR: could not history off t_re_aid_elig\n");
              fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
              fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
              return EXIT_FAILURE;
           }

  return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   historyAidElig()                                   */
/*                                                                      */
/*  Description:    Inactivate bene's aid elig segment                  */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   11/17/2003 James Mercer     Initial Release                */
/************************************************************************/
static int historyAidElig(int sak)
{

exec sql begin declare section;
int sak_aid_elig;
exec sql end declare section;

          sak_aid_elig = sak;

          sqlca.sqlcode = 0;
          EXEC SQL
            UPDATE t_re_aid_elig
            SET cde_status1 = 'H',
                dte_last_updated = :curr_dte,
                dte_active_thru = :curr_dte
            WHERE sak_aid_elig = :sak_aid_elig;

           if (sqlca.sqlcode != 0)
           {
              fprintf(stderr, "ERROR: could not history off t_re_aid_elig\n");
              fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
              fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
              return EXIT_FAILURE;
           }

  return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateEligSeg()                                    */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int updateEligSeg(int eff, int end, int sak, int month, int aid_eff,
                         int aid_end, int elig_index)
{

exec sql begin declare section;
int open_date = OPEN_DATE;
int dte_eff;
int dte_end;
int sak_pgm_elig;
exec sql end declare section;

          sak_pgm_elig = sak;
          dte_eff = eff;
          case_dte_cert = eff;
          dte_end = end;

          sqlca.sqlcode = 0;
          EXEC SQL
            UPDATE t_re_elig
            SET dte_effective = :dte_eff,
                dte_end = :dte_end,
                cde_status1 = ' ',
                dte_last_updated = :curr_dte,
                dte_active_thru = :open_date
            WHERE sak_recip = :sak_recip and sak_pgm_elig = :sak_pgm_elig;

           if (sqlca.sqlcode != 0)
           {
              fprintf(stderr, "ERROR: could not update into t_re_elig\n");
              fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
              fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
              return EXIT_FAILURE;
           }

          if (updateAidElig(aid_eff, aid_end, sak, month, elig_index) != 0)
          {
             fprintf (stderr, "ERROR: bad return from updateAidElig\n");
             return EXIT_FAILURE;
          }

    if (memcmp(prog_type, "MP", sizeof(prog_type)) == 0)
    {
       if (povPercent() != 0)
       {
          fprintf (stderr, "ERROR: bad return from povPercent\n");
          return EXIT_FAILURE;
       }
    }

    bAddedRecord = TRUE;

    if (insertIDCard(sak_recip) != 0)
    {
       fprintf (stderr, "ERROR: bad return from insertIDCard\n");
       return EXIT_FAILURE;
    }

          return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateAidElig()                                    */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int updateAidElig(int eff, int end, int sak_pgm, int month, int elig_index)
{

int sak_aid;
int dte = 0;
int dte_end;
int last_update;
int active_thru;
char status[1+1]=" ";
int tmp_benefit_month_yyyymmdd;
int tmp_benefit_month_lastday;
int flag = FALSE;

    /*save globals and reset later*/
    tmp_benefit_month_yyyymmdd = benefit_month_yyyymmdd;
    tmp_benefit_month_lastday = benefit_month_lastday;

    if (getSakPopCode() != 0)
    {
        fprintf (stderr, "ERROR: bad return from getSakPopCode\n");
        return EXIT_FAILURE;
    }

    if (checkIfAidsDiffer(&flag, elig_index) != 0)
    {
       fprintf(stderr, "ERROR: bad return from checkIfAidsDiffer\n");
       return EXIT_FAILURE;
    }

    if (bCaseVerified == FALSE)
    {
        if (processCase() != 0)
        {
           fprintf (stderr, "ERROR: bad return from processCase\n");
           return EXIT_FAILURE;
        }
    }

    if (flag == TRUE || elig_sak_case[elig_index] != sak_case )
    {
       elig_sak_recip = sak_recip;
       new_sak_pgm_elig = sak_pgm;
       benefit_month_yyyymmdd = month;
       benefit_month_lastday = end;

       if (insertAidEligSeg(elig_sak_case[elig_index]) != 0)
       {
          fprintf (stderr, "ERROR: bad return from insertAidEligSeg\n");
          return EXIT_FAILURE;
       }

     }
     else
     {
       if (selectAidEligSeg(&sak_aid, &dte, sak_pgm) != 0)
       {
           fprintf (stderr, "ERROR: bad return from selectAidEligSeg\n");
           return EXIT_FAILURE;
       }

       if (dte == benefit_month_yyyymmdd)
       {
          dte_end = end;
          last_update = curr_dte;
          active_thru = OPEN_DATE;
          status[0] = ' ';

          if (updateAidEligSeg(&sak_aid, sak_pgm, status, dte_end, last_update, active_thru) != 0)
          {
             fprintf (stderr, "ERROR: bad return from updateAidEligSeg\n");
             return EXIT_FAILURE;
          }
       }
       else
       {
          elig_sak_recip = sak_recip;
          new_sak_pgm_elig = sak_pgm;
          benefit_month_yyyymmdd = month;
          benefit_month_lastday = end;

          if (insertAidEligSeg(elig_sak_case[elig_index]) != 0)
          {
             fprintf (stderr, "ERROR: bad return from insertAidEligSeg\n");
             return EXIT_FAILURE;
          }

        }
     }

     /* reset globals */
     benefit_month_yyyymmdd = tmp_benefit_month_yyyymmdd;
     benefit_month_lastday = tmp_benefit_month_lastday;

           return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateElig()                                       */
/*                                                                      */
/*  Description:     Updates the dates of an elig record                */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   11/17/2003 James Mercer     Initial Release                */
/************************************************************************/
static int updateElig(int eff, int end, int sak)
{

exec sql begin declare section;
int open_date = OPEN_DATE;
int dte_eff;
int dte_end;
int sak_pgm_elig;
exec sql end declare section;

       sak_pgm_elig = sak;
       dte_eff      = eff;
       dte_end      = end;

       sqlca.sqlcode = 0;
       EXEC SQL
         UPDATE t_re_elig
         SET dte_effective    = :dte_eff,
             dte_end          = :dte_end,
             cde_status1      = ' ',
             dte_last_updated = :curr_dte,
             dte_active_thru  = :open_date
         WHERE sak_recip      = :sak_recip
          AND sak_pgm_elig    = :sak_pgm_elig;

       if (sqlca.sqlcode != 0)
       {
          fprintf(stderr, "ERROR: could not update into t_re_elig\n");
          fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   checkIfAidsDiffer()                                */
/*                                                                      */
/*  Description:    Determines if the input and db aid Elig records     */
/*                  are different.                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   09/23/2003 James Mercer      Initial Release               */
/*  CO8831   02/28/2006 Srinivas Donepudi Added pres_dis_ind logic      */
/************************************************************************/
static int checkIfAidsDiffer(int *flag, int elig_index)
{

   /* compare the sak_cde_aid and all the indicators */
   /* (the first difference detected will "short circuit" to the else)*/
   if (        sak_cde_aid == elig_sak_cde_aid[elig_index]         &&
       strcmp( prog_type,     elig_prog_type[elig_index]    ) == 0 &&
       strcmp( prog_subtype,  elig_prog_subtype[elig_index] ) == 0 &&
       strcmp( med_subtype,   elig_med_subtype[elig_index]  ) == 0 &&
       strcmp( med_elig_ind,  elig_med_elig_ind[elig_index] ) == 0 &&
       strcmp( cash_prog,     elig_cash_prog[elig_index]    ) == 0 &&
       strcmp( cash_prog_sb,  elig_cash_prog_sb[elig_index] ) == 0 &&
       strcmp( src_fund,      elig_src_fund[elig_index]     ) == 0 &&
       strcmp( placement,     elig_typ_place[elig_index]    ) == 0 &&
       strcmp( legal_status,  elig_legal_status[elig_index] ) == 0 &&
       strcmp( qmb_ind,       elig_qmb_ind[elig_index]      ) == 0 &&
       strcmp( part_code,     elig_partic[elig_index]       ) == 0 &&
       strcmp( pres_dis_ind,  elig_pres_dis_ind[elig_index] ) == 0   )
   {
           *flag = FALSE;
   }
   else
   {
           *flag = TRUE;
   }

   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   selectAidEligSeg()                                 */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int selectAidEligSeg(int *sak, int *dte, int sak_pgm)
{

exec sql begin declare section;
int sak_pgm_elig;
int sak_aid_elig;
int dte_end;
exec sql end declare section;

    sak_pgm_elig = sak_pgm;
    dte_end = 0; /*(to keep dte from returning junk if aid elig not found)*/

    sqlca.sqlcode = 0;
    EXEC SQL
      SELECT nvl(max(sak_aid_elig),0)
      INTO   :sak_aid_elig
      FROM  t_re_aid_elig
      WHERE sak_recip = :sak_recip
      AND   sak_pgm_elig = :sak_pgm_elig
      AND   sak_cde_aid = :sak_cde_aid;

    if (sqlca.sqlcode != 0)
    {
       fprintf(stderr, "ERROR: could not select sak aid elig from t_re_aid_elig table\n");
       fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
       fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
       return EXIT_FAILURE;
    }

    if (sak_aid_elig != 0)
    {
       sqlca.sqlcode = 0;
         EXEC SQL
           SELECT  to_number(to_char(to_date(dte_end, 'YYYYMMDD') +1, 'YYYYMMDD'))
           INTO   :dte_end
           FROM  t_re_aid_elig
           WHERE sak_recip = :sak_recip
           AND   sak_aid_elig = :sak_aid_elig;

       if (sqlca.sqlcode != 0)
       {
          fprintf(stderr, "ERROR: could not select dte end from t_re_aid_elig table\n");
          fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }
    }

    *sak = sak_aid_elig;
    *dte = dte_end;

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateAidEligSeg()                                 */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int updateAidEligSeg(int *sak_aid, int sak_pgm, char status[], int end, int last_updated, int dte_active)
{

exec sql begin declare section;
int dte_end;
int dte_last_update;
int dte_active_thru;
int sak_aid_elig;
int sak_pgm_elig;
char status1[1+1] = " ";
exec sql end declare section;

          dte_end = end;
          dte_last_update = last_updated;
          dte_active_thru = dte_active;
          status1[0] = status[0];
          sak_aid_elig = *sak_aid;
          sak_pgm_elig = sak_pgm;

      if (dte_end != 0)
      {
          sqlca.sqlcode = 0;
          EXEC SQL
            UPDATE t_re_aid_elig
            SET dte_end = :dte_end,
                cde_status1 = :status1,
                dte_last_updated = :dte_last_update,
                dte_active_thru = :dte_active_thru
            WHERE sak_recip = :sak_recip
            AND sak_pgm_elig = :sak_pgm_elig
            AND sak_aid_elig = :sak_aid_elig;

           if (sqlca.sqlcode != 0)
           {
              fprintf(stderr, "ERROR: could not update t_re_aid_elig\n");
              fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
              fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
              return EXIT_FAILURE;
           }
        }
        else
        {

          sqlca.sqlcode = 0;
          EXEC SQL
            UPDATE t_re_aid_elig
            SET cde_status1 = :status1,
                dte_last_updated = :dte_last_update,
                dte_active_thru = :dte_active_thru
            WHERE sak_recip = :sak_recip
            AND sak_pgm_elig = :sak_pgm_elig
            AND sak_aid_elig = :sak_aid_elig;

           if (sqlca.sqlcode != 0)
           {
              fprintf(stderr, "ERROR: could not update t_re_aid_elig\n");
              fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
              fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
              return EXIT_FAILURE;
           }
        }

           return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   replaceAidElig()                                   */
/*                                                                      */
/*  Description:    Replace an existing aid elig record with the input  */
/*                  aid elig.                                           */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   11/17/2003 James Mercer      Initial Release               */
/*  CO11847  04/15/2009 Dale Gallaway    Premium Billing project        */
/************************************************************************/
static int replaceAidElig(int replace_aid_index, int input_rec_index)
{
int temp_dte_eff;
int temp_dte_end;

    /*history aid elig*/
    if (historyAidElig(elig_sak_aid_elig[replace_aid_index]) != 0)
    {
       fprintf (stderr, "ERROR: bad return from historyAidElig\n");
       return EXIT_FAILURE;
    }

    /*insert new aid elig*/
    elig_sak_recip = sak_recip;
    new_sak_pgm_elig = elig_sak_pgm_elig[replace_aid_index];

    /*(retrieve sak_cde_aid)*/
    if (getSakPopCode() != 0)
    {
        fprintf (stderr, "ERROR: bad return from getSakPopCode\n");
        return EXIT_FAILURE;
    }

    if (insertAidEligSeg(elig_sak_case[replace_aid_index]) != 0)
    {
        fprintf (stderr, "ERROR: bad return from insertAidEligSeg\n");
        return EXIT_FAILURE;
    }

    /*if existing begins b4 new, insert existing b4*/
    if (aid_elig_dte_eff[replace_aid_index] < benefit_month_yyyymmdd)
    {
       temp_dte_eff = benefit_month_yyyymmdd;
       temp_dte_end = benefit_month_lastday;

       benefit_month_yyyymmdd = aid_elig_dte_eff[replace_aid_index];
       benefit_month_lastday = benefit_month_min1day;

       if (copyAidEligSeg(replace_aid_index) != 0)
       {
          fprintf (stderr, "ERROR: bad return from copyAidEligSeg\n");
          return EXIT_FAILURE;
       }

       benefit_month_yyyymmdd = temp_dte_eff;
       benefit_month_lastday = temp_dte_end;
    }

    /*if existing after new, insert existing after*/
    if (aid_elig_dte_end[replace_aid_index] > benefit_month_lastday)
    {
       temp_dte_eff = benefit_month_yyyymmdd;
       temp_dte_end = benefit_month_lastday;

       benefit_month_yyyymmdd = benefit_month_lastday_pls1;
       benefit_month_lastday = aid_elig_dte_end[replace_aid_index];

       if (copyAidEligSeg(replace_aid_index) != 0)
       {
          fprintf (stderr, "ERROR: bad return from copyAidEligSeg\n");
          return EXIT_FAILURE;
       }

       benefit_month_yyyymmdd = temp_dte_eff;
       benefit_month_lastday = temp_dte_end;
    }

    /*update elig dates (if necessary)*/
    if ( benefit_month_yyyymmdd < elig_dte_eff[replace_aid_index] ||
         benefit_month_lastday > elig_dte_end[replace_aid_index]     )
    {
       if ( benefit_month_yyyymmdd < elig_dte_eff[replace_aid_index] )
          temp_dte_eff = benefit_month_yyyymmdd;
       else
          temp_dte_eff = elig_dte_eff[replace_aid_index];

       idayofmon = elig_dte_eff [replace_aid_index] % 100;

/* CO11869C back out
       if (is_future_month == FALSE && idayofmon > 1
       &&  sak_pub_hlth[0] == 4     && retro_t21[0] != 'Y')
       {
             / * CO11869c     Do this because month-end has run,   * / 
          temp_dte_eff   = elig_dte_eff [replace_aid_index];
       }
*/
       if ( benefit_month_lastday > elig_dte_end[replace_aid_index] )
          temp_dte_end = benefit_month_lastday;
       else
          temp_dte_end = elig_dte_end[replace_aid_index];

       if (updateElig(temp_dte_eff, temp_dte_end, elig_sak_pgm_elig[replace_aid_index]) != 0)
       {
          fprintf (stderr, "ERROR: bad return from updateElig\n");
          return EXIT_FAILURE;
       }
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   extendElig()                                       */
/*                                                                      */
/*  Description:    Extend an Elig & Aid Elig rec.  (Histories both.)   */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO5705   03/16/2004 James Mercer      Initial Release               */
/************************************************************************/
static int extendElig(int extend_aid_index)
{
    int temp_dte_eff;
    int temp_dte_end;
    int flag = FALSE;

    temp_dte_eff = benefit_month_yyyymmdd;
    temp_dte_end = benefit_month_lastday;

    benefit_month_yyyymmdd = elig_dte_eff[extend_aid_index];

    /*insert new elig*/
    if (insertEligSeg(elig_sak_pub_hlth[extend_aid_index], elig_num_rank[extend_aid_index]) != 0)
    {
        fprintf (stderr, "ERROR: bad return from insertEligSeg\n");
        return EXIT_FAILURE;
    }

    benefit_month_yyyymmdd = temp_dte_eff;
    benefit_month_lastday = temp_dte_end;

    /*check if parms differ*/
    if (getSakPopCode() != 0)
    {
        fprintf (stderr, "ERROR: bad return from getSakPopCode\n");
        return EXIT_FAILURE;
    }

    if (checkIfAidsDiffer(&flag, extend_aid_index) != 0)
    {
       fprintf(stderr, "ERROR: bad return from checkIfAidsDiffer\n");
       return EXIT_FAILURE;
    }

    if (flag == TRUE)
    {
       /*copy all aid eligs (don't extend)*/
       if (copyExtendAidEligs(extend_aid_index, FALSE) != 0)
       {
          fprintf(stderr, "ERROR: bad return from copyExtendAidEligs\n");
          return EXIT_FAILURE;
       }

       /*insert an aid elig with the differing input parms*/
       if (insertAidEligSeg(elig_sak_case[extend_aid_index]) != 0)
       {
          fprintf (stderr, "ERROR: bad return from insertAidEligSeg\n");
          return EXIT_FAILURE;
       }
    }
    else
    {
       /*copy all aid eligs (extend appropriate one)*/
       if (copyExtendAidEligs(extend_aid_index, TRUE) != 0)
       {
          fprintf(stderr, "ERROR: bad return from copyExtendAidEligs\n");
          return EXIT_FAILURE;
       }
    }

    /*history elig & aid eligs*/
    if (historyElig(elig_sak_pgm_elig[extend_aid_index]) != 0)
    {
        fprintf(stderr, "ERROR: bad return from historyElig\n");
        return EXIT_FAILURE;
    }

    if (memcmp(prog_type, "MP", sizeof(prog_type)) == 0)
    {
       if (povPercent() != 0)
       {
          fprintf (stderr, "ERROR: bad return from povPercent\n");
          return EXIT_FAILURE;
       }
    }

    bAddedRecord = TRUE;

    if (insertIDCard(sak_recip) != 0)
    {
       fprintf (stderr, "ERROR: bad return from insertIDCard\n");
       return EXIT_FAILURE;
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertIDCard()                                     */
/*                                                                      */
/*  Description:     Insert a row to the t_re_id_crd_iss table when a   */
/*                   bene. is newly eligible for the current month.     */
/*                   The ID Card process will use this table as a driver*/
/*                   to know that an ID card must be generated.         */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  11243    06/26/2008 Srinivas Donepudi Modified to add card reason   */
/************************************************************************/
static int insertIDCard(int sak)
{

exec sql begin declare section;
int id_sak_recip;
char id_source[8+1]="BATCH";
char cde_source[1+1]="N";
char card_rsn [2+1] = "00";  /* System */
exec sql end declare section;

   if (id_card_printed == FALSE)
   {
      id_sak_recip = sak;

      sqlca.sqlcode = 0;
      EXEC SQL
        INSERT INTO t_re_id_crd_iss
               (sak_recip,
                id_source,
                cde_source,
                dte_issue,
                dte_coverage_begin,
                dte_coverage_end,
                cde_id_card_rsn)
        VALUES (:id_sak_recip,
                :id_source,
                :cde_source,
                0,
                :id_card_benefit_start,
                :id_card_benefit_end,
                :card_rsn);

              if (sqlca.sqlcode != 0)
              {
                 fprintf(stderr, "ERROR: could not insert into t_re_id_crd_iss\n");
                 fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
                 fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
                 return EXIT_FAILURE;
              }
      id_card_printed = TRUE;
   }

 return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   declareElig()                                      */
/*                                                                      */
/*  Description:    Declare Eligibility cursor                          */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO8831   02/28/2006 Srinivas D       Added PDI field to SELECT      */
/************************************************************************/
static int declareElig()
{

    sqlca.sqlcode = 0;
    EXEC SQL
      DECLARE ELIGCSR CURSOR FOR
        SELECT e.sak_pgm_elig,
               e.sak_pub_hlth,
               e.dte_effective,
               e.dte_end,
               a.dte_effective,
               a.dte_end,
               e.cde_status1,
               e.num_rank,
               a.sak_aid_elig,
               a.sak_cde_aid,
               a.sak_case,
               a.kes_cde_legal_stat,
               a.kes_cde_cash_type,
               a.kes_cde_cash_sub,
               a.kes_cde_fund_src,
               a.kes_cde_typ_place,
               a.kes_cde_med_elig,
               a.kes_cde_inv_med_sb,
               a.kes_cde_med_type,
               a.kes_cde_med_sub,
               a.kes_ind_qmb,
               a.kes_cde_partic,
               a.kes_cde_pdi,
               c.cde_aid_category,
               z.cde_citizen_stat,
               trunc(months_between(SYSDATE, (to_date(b.dte_birth, 'YYYYMMDD')))/12),
               p.ind_cred_covg_cert
        FROM  t_re_elig e, t_pub_hlth_pgm p, t_re_aid_elig a,
              t_cde_aid c, t_re_base b, t_re_citizen_dsc z
        WHERE e.sak_recip = :sak_recip
        AND   e.cde_status1 = ' '
        AND   e.sak_pub_hlth = p.sak_pub_hlth
        AND   p.cde_type in ('BO', 'DU', 'MJ', 'SA')
	AND   p.cde_pgm_health <> 'PACE'
	AND   p.cde_pgm_health <> 'ADAPD'
	AND   p.cde_pgm_health <> 'ADAPT'
        AND   e.sak_recip = a.sak_recip
        AND   e.sak_pgm_elig = a.sak_pgm_elig
        AND   a.cde_status1 = ' '
        AND   a.sak_cde_aid = c.sak_cde_aid
        AND   e.sak_recip = b.sak_recip
        AND   b.sak_citizen_dsc = z.sak_citizen_dsc
   AND  (:benefit_month_min1day      = a.dte_end OR         /*right b4 OR */
         :benefit_month_lastday_pls1 = a.dte_effective OR   /*right after OR*/
         (:benefit_month_yyyymmdd <= a.dte_end AND           /*overlaps*/
          :benefit_month_lastday >= a.dte_effective ) )
     ORDER BY decode(e.num_rank,10,120,e.num_rank) desc;

     if (sqlca.sqlcode != 0)
     {
        fprintf(stderr, "ERROR: could not declare ELIGCSR\n");
        fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }

     sqlca.sqlcode = 0;
     EXEC SQL
       OPEN ELIGCSR;

     if (sqlca.sqlcode != 0)
     {
        fprintf(stderr, "ERROR: could not open ELIGCSR\n");
        fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }

     return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   fetchElig()                                        */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int fetchElig()
{

    sqlca.sqlcode = 0;
    EXEC SQL
      FETCH ELIGCSR INTO
          :elig_sak_pgm_elig,
          :elig_sak_pub_hlth,
          :elig_dte_eff,
          :elig_dte_end,
          :aid_elig_dte_eff,
          :aid_elig_dte_end,
          :elig_cde_status1,
          :elig_num_rank,
          :elig_sak_aid_elig,
          :elig_sak_cde_aid,
          :elig_sak_case,
          :elig_legal_status,
          :elig_cash_prog,
          :elig_cash_prog_sb,
          :elig_src_fund,
          :elig_typ_place,
          :elig_med_elig_ind,
          :elig_med_subtype,
          :elig_prog_type,
          :elig_prog_subtype,
          :elig_qmb_ind,
          :elig_partic,
          :elig_pres_dis_ind,
          :elig_pop_code,
          :elig_citizen,
          :elig_epsdt_age,
          :elig_covg_cert;

     if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
     {
        fprintf(stderr, "ERROR: could not fetch ELIGCSR\n");
        fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }

     if (sqlca.sqlerrd[2] >= MAX_ELIG_ARRAY)
     {
        fprintf(stderr, "ERROR: ELIGCSR retrieved more than max allowed of %d\n", MAX_ELIG_ARRAY);
        fprintf(stderr, "ERROR: ID Medicaid %s, sak_recip %d\n", id_medicaid, sak_recip);
        return EXIT_FAILURE;
     }

     return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   declareAidElig()                                   */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO8831   02/28/2006 Srinivas D       Added PDI field to the SELECT  */
/************************************************************************/
static int declareAidElig(int in_sak_pgm_elig)
{
exec sql begin declare section;
int sak_pgm_elig;
exec sql end declare section;

    sak_pgm_elig = in_sak_pgm_elig;

    sqlca.sqlcode = 0;
    EXEC SQL
      DECLARE AIDCSR CURSOR FOR
        SELECT sak_aid_elig,
               sak_cde_aid,
               sak_case,
               dte_effective,
               dte_end,
               cde_status1,
               kes_cde_med_type,
               kes_cde_med_sub,
               kes_cde_inv_med_sb,
               kes_cde_med_elig,
               kes_cde_cash_type,
               kes_cde_cash_sub,
               kes_cde_fund_src,
               kes_cde_typ_place,
               kes_cde_legal_stat,
               kes_ind_qmb,
               kes_cde_partic,
               kes_cde_lime,
               kes_cde_lime_title,
               kes_cde_pdi,
               dte_created,           
               dte_last_updated,
               dte_active_thru        
        FROM t_re_aid_elig
        WHERE sak_recip    = :elig_sak_recip
        AND   sak_pgm_elig = :sak_pgm_elig;

     if (sqlca.sqlcode != 0)
     {
        fprintf(stderr, "ERROR: could not declare AIDCSR\n");
        fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }


     sqlca.sqlcode = 0;
     EXEC SQL
       OPEN AIDCSR;

     if (sqlca.sqlcode != 0)
     {
        fprintf(stderr, "ERROR: could not open AIDCSR\n");
        fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }

     return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   fetchAidElig()                                     */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO8831   02/28/2006 Srinivas D       Added PDI field to FETCH AIDCSR*/
/************************************************************************/
static int fetchAidElig()
{

    sqlca.sqlcode = 0;
    EXEC SQL
      FETCH AIDCSR INTO
          :aid_sak_aid_elig,
          :aid_sak_cde_aid,
          :aid_sak_case,
          :aid_dte_eff,
          :aid_dte_end,
          :aid_cde_status1,
          :aid_kes_cde_med_type,
          :aid_kes_cde_med_sub,
          :aid_kes_cde_inv_med_sb,
          :aid_kes_cde_med_elig,
          :aid_kes_cde_cash_type,
          :aid_kes_cde_cash_sub,
          :aid_kes_cde_fund_src,
          :aid_kes_cde_typ_place,
          :aid_kes_cde_legal_stat,
          :aid_kes_ind_qmb,
          :aid_kes_cde_partic,
          :aid_kes_cde_lime,
          :aid_kes_cde_lime_title,
          :aid_kes_cde_pdi,
          :aid_dte_created,
          :aid_dte_last_updated,
          :aid_dte_active_thru;

     if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
     {
        fprintf(stderr, "ERROR: could not fetch AIDCSR\n");
        fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }

     if (sqlca.sqlerrd[2] >= MAX_AID_ARRAY)
     {
        fprintf(stderr, "ERROR: AIDCSR retrieved more than max allowed of %d\n", MAX_AID_ARRAY);
        fprintf(stderr, "ERROR: ID Medicaid %s, sak_recip %d\n", id_medicaid, elig_sak_recip);
        return EXIT_FAILURE;
     }

     return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   compareAidEligCase()                               */
/*                                                                      */
/*  Description:    Insert Elig Aid record with values from existing    */
/*                  elig aid record.                                    */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO6816   09/08/2004 Lisa Salgat       Initial Release               */
/************************************************************************/
static int compareAidEligCase(int orig_case)
{

int  bEqualStart = FALSE;
int  bOrigCase   = FALSE;
int tmp_start   = 0;
int tmp_end     = 0;

    if (bCaseVerified == FALSE)
    {
        if (processCase() != 0)
        {
           fprintf (stderr, "ERROR: bad return from processCase\n");
           return EXIT_FAILURE;
        }
    }

    aid_elig_start[0] = benefit_month_yyyymmdd;
    aid_elig_end[0]   = benefit_month_lastday;
    aid_elig_case[0]  = sak_case;
    case_index = 0;

    if (orig_case != sak_case &&
        orig_case != 0)
    {
      bene_sak_case = sak_case;
      bEqualStart = FALSE;
      tmp_start   = benefit_month_yyyymmdd/100;
      tmp_end     = benefit_month_lastday/100;

      /* verify time period includes new benefit month and includes more than new benefit month */
      if (benefit_month_yyyymm < tmp_start || benefit_month_yyyymm > tmp_end)
      {
           aid_elig_case[0]  = orig_case;
           bOrigCase         = TRUE;
      }
         
      if ((benefit_month_yyyymm != tmp_start || benefit_month_yyyymm != tmp_end) && bOrigCase == FALSE)
      {
         if (benefit_month_yyyymm == tmp_start)
         {
             aid_elig_end[case_index]   = orig_benefit_month_lastday;
             case_index++;
             aid_elig_start[case_index] = orig_benefit_month_lastday_pls1;
             bEqualStart = TRUE;
         }
         else
         {
             aid_elig_end[case_index]   = orig_benefit_month_min1day;
             aid_elig_case[case_index]  = orig_case;
             case_index++;
             aid_elig_start[case_index] = (benefit_month_yyyymm * 100) + 1;
         }

         aid_elig_end[case_index]  = benefit_month_lastday;
         aid_elig_case[case_index] = sak_case;

         if (benefit_month_yyyymm != tmp_end)
         {
             if (bEqualStart)
                aid_elig_case[case_index]  = orig_case;
             else 
             {
                aid_elig_end[case_index]   = orig_benefit_month_lastday;
                case_index++;
                aid_elig_case[case_index]  = orig_case;
                aid_elig_start[case_index] = orig_benefit_month_lastday_pls1;
                aid_elig_end[case_index]   = benefit_month_lastday;
             }
         }
      }
    }
/* CO11869 back out
    else
    {
       idayofmon = elig_dte_eff [0] % 100;

       if (is_future_month == FALSE && idayofmon > 1
       &&  sak_pub_hlth[0] == 4     && retro_t21[0] != 'Y')
       {
             / * CO11869c     Do this because month-end has run,   * / 
          aid_elig_start [0] = elig_dte_eff [0];
       }
    } */ 
    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   copyAidElig()                                      */
/*                                                                      */
/*  Description:    Insert Elig Aid record with values from existing    */
/*                  elig aid record.                                    */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO5705   03/25/2004 James Mercer      Initial Release               */
/*  CO8831   02/28/2006 Srinivas Donepudi Added PDI field to INSERT     */
/************************************************************************/
static int copyAidElig(int in_aid_index)
{
int i = 0; 

    if (compareAidEligCase(aid_sak_case[in_aid_index]) != 0)
    {
       fprintf (stderr, "ERROR: bad return from compareAidEligCase\n");
       return EXIT_FAILURE;
    }

    for (i=0; i <= case_index; i++)
    {
       if (getNewSakAidElig() != 0)
       {
          fprintf (stderr, "ERROR: bad return from getNewSakAidElig\n");
          return EXIT_FAILURE;
       }
          sqlca.sqlcode = 0;
          EXEC SQL
            INSERT INTO t_re_aid_elig
                   (sak_aid_elig,
                    sak_recip,
                    sak_pgm_elig,
                    sak_cde_aid,
                    sak_case,
                    dte_effective,
                    dte_end,
                    cde_status1,
                    kes_cde_med_type,
                    kes_cde_med_sub,
                    kes_cde_inv_med_sb,
                    kes_cde_med_elig,
                    kes_cde_cash_type,
                    kes_cde_cash_sub,
                    kes_cde_fund_src,
                    kes_cde_typ_place,
                    kes_cde_legal_stat,
                    kes_ind_qmb,
                    kes_cde_partic,
                    kes_cde_lime,
                    kes_cde_lime_title,
                    kes_cde_pdi,
                    dte_created,
                    dte_last_updated,
                    dte_active_thru)
            VALUES (:new_sak_aid_elig,
                    :elig_sak_recip,
                    :new_sak_pgm_elig,
                    :aid_sak_cde_aid[in_aid_index],
                    :aid_elig_case[i],
                    :aid_elig_start[i],
                    :aid_elig_end[i],
                    :aid_cde_status1[in_aid_index],
                    :aid_kes_cde_med_type[in_aid_index],
                    :aid_kes_cde_med_sub[in_aid_index],
                    :aid_kes_cde_inv_med_sb[in_aid_index],
                    :aid_kes_cde_med_elig[in_aid_index],
                    :aid_kes_cde_cash_type[in_aid_index],
                    :aid_kes_cde_cash_sub[in_aid_index],
                    :aid_kes_cde_fund_src[in_aid_index],
                    :aid_kes_cde_typ_place[in_aid_index],
                    :aid_kes_cde_legal_stat[in_aid_index],
                    :aid_kes_ind_qmb[in_aid_index],
                    :aid_kes_cde_partic[in_aid_index],
                    ' ',
                    ' ',
                    :aid_kes_cde_pdi[in_aid_index],
                    :aid_dte_created[in_aid_index],
                    :aid_dte_last_updated[in_aid_index],
                    :aid_dte_active_thru[in_aid_index]);

           if (sqlca.sqlcode != 0)
           {
              fprintf(stderr, "ERROR: could not insert into t_re_aid_elig\n");
              fprintf(stderr,"ERROR: ID Medicaid%s\n", id_medicaid);
              fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
              return EXIT_FAILURE;
           }
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   copyExtendAidEligs()                               */
/*                                                                      */
/*  Description:    copies all aid elig recs for an elig and extends    */
/*                  one of them.                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO5705   03/25/2004 James Mercer      Initial Release               */
/************************************************************************/
static int copyExtendAidEligs(int in_aid_index, int extend)
{

    int aid_elig_seg_cnt = 0;
    int i;
    int temp_dte_eff;
    int temp_dte_end;

    /* get all aid elig recs for the elig */
    if (declareAidElig(elig_sak_pgm_elig[in_aid_index]) != 0)
    {
       fprintf (stderr, "ERROR: bad return from declareAidElig\n");
       return EXIT_FAILURE;
    }

    if (fetchAidElig() != 0)
    {
       fprintf (stderr, "ERROR: bad return from fetchAidElig\n");
       return EXIT_FAILURE;
    }

    aid_elig_seg_cnt = sqlca.sqlerrd[2];

    temp_dte_eff = benefit_month_yyyymmdd;
    temp_dte_end = benefit_month_lastday;

    /*spin thru aid elig recs...*/
    for (i=0; i < aid_elig_seg_cnt; i++)
    {
        /*set date range for new aid rec...*/
        benefit_month_yyyymmdd = aid_dte_eff[i];

        /*...if the aid elig rec being extended, use bene month end*/
        if (extend == TRUE && aid_sak_aid_elig[i] == elig_sak_aid_elig[in_aid_index])
            benefit_month_lastday = temp_dte_end;
        else
            benefit_month_lastday = aid_dte_end[i];

        /*create a new aid elig from selected one*/
        if (copyAidElig(i) != 0)
        {
           fprintf (stderr, "ERROR: bad return from copyAidElig\n");
           return EXIT_FAILURE;
        }
    }

    benefit_month_yyyymmdd = temp_dte_eff;
    benefit_month_lastday = temp_dte_end;

    sqlca.sqlcode = 0;
    EXEC SQL
      CLOSE AIDCSR;

    if (sqlca.sqlcode != 0)
    {
       fprintf(stderr, "ERROR: could not close AIDCSR\n");
       fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
       return EXIT_FAILURE;
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   copyAidEligs()                                     */
/*                                                                      */
/*  Description:    copies all aid elig recs for an elig within a date  */
/*                  range.                                              */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO5705   04/15/2004 James Mercer      Initial Release               */
/************************************************************************/
static int copyAidEligs(int in_aid_index)
{
    int aid_elig_seg_cnt = 0;
    int i;
    int temp_dte_eff;
    int temp_dte_end;

    /* get all aid elig recs for the elig */
    if (declareAidElig(elig_sak_pgm_elig[in_aid_index]) != 0)
    {
       fprintf (stderr, "ERROR: bad return from declareAidElig\n");
       return EXIT_FAILURE;
    }

    if (fetchAidElig() != 0)
    {
       fprintf (stderr, "ERROR: bad return from fetchAidElig\n");
       return EXIT_FAILURE;
    }

    aid_elig_seg_cnt = sqlca.sqlerrd[2];

    temp_dte_eff = benefit_month_yyyymmdd;
    temp_dte_end = benefit_month_lastday;

    /*spin thru aid elig recs...*/
    for (i=0; i < aid_elig_seg_cnt; i++)
    {
        /*set date range for new aid rec...*/
        benefit_month_yyyymmdd = aid_dte_eff[i];
        benefit_month_lastday = aid_dte_end[i];

        /*check if aid elig begins before date range*/
        if (benefit_month_yyyymmdd < temp_dte_eff)
        {
           if (benefit_month_lastday < temp_dte_eff)
           {
              /*all of aid elig before date range...don't copy*/
              continue;
           }

           /*else, start aid at start of date range*/
           benefit_month_yyyymmdd = temp_dte_eff;
        }

        /*check if aid elig ends after date range*/
        if (benefit_month_lastday > temp_dte_end)
        {
           if (benefit_month_yyyymmdd > temp_dte_end)
           {
              /*all of aid elig after date range...don't copy*/
              continue;
           }

           /*else, end aid at end of date range*/
           benefit_month_lastday = temp_dte_end;
        }

        /*create a new aid elig from selected one*/
        if (copyAidElig(i) != 0)
        {
           fprintf (stderr, "ERROR: bad return from copyAidElig\n");
           return EXIT_FAILURE;
        }
    }

    benefit_month_yyyymmdd = temp_dte_eff;
    benefit_month_lastday = temp_dte_end;

    sqlca.sqlcode = 0;
    EXEC SQL
      CLOSE AIDCSR;

    if (sqlca.sqlcode != 0)
    {
       fprintf(stderr, "ERROR: could not close AIDCSR\n");
       fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
       return EXIT_FAILURE;
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   rankMN(int)                                       */
/*                                                                      */
/*  Description:    Verify if Medical Needy benefit plan has met        */
/*                  SpendDown requirement                               */
/*                  met     => rank = 120                               */
/*                  not met => rank = 10                                */
/************************************************************************/
static int rankMN(int elig_seg_count)
{

int i;
int SDmet = FALSE;

    for (i = 0; i < elig_seg_count; i++)
    {
        if (elig_sak_pub_hlth[i] == HLTH_MN)  /* Is Medically Needy BP */
        {
           if (checkSpndwn(&SDmet) != 0)
           {
              fprintf (stderr, "ERROR: bad return from checkSpndwn - rankMN\n");
              return EXIT_FAILURE;
           }

           if (SDmet == FALSE)
           {
               elig_num_rank[i] = 10;
           }
           else
           {
               elig_num_rank[i] = 120;
           }

        }
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   benefitMth()                                       */
/*                                                                      */
/*  Description: Process KAECSES txns against eligibility tables.       */
/*               When KAECSES eligibility info. is for new bene, estab. */
/*               eligibility from the 1st through the end of the month. */
/*               For current eligible bene - rcvd. eligibility is for   */
/*               the next month as compared to the processing month,    */
/*               extend the existing eligibility segment by setting the */
/*               eligibility thru date to be the end of the next month  */
/*               For T21 bene. parm date will be current date + 1,      */
/*               a flag called t21_retro is used to process  retroactive*/
/*               T21 eligibility. Non-T21 retroactive eligibility will  */
/*               be processed regardless of this flag                   */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO8831   02/02/2006 Srinivas D       Added pdi field to checkMultiPlan*/
/*  CO9489   05/26/2006 Srinivas D       Modified for pres eligibility  */
/*                                       to include additional PE rules */
/*  CO11847  04/15/2009 Dale Gallaway    Premium Billing project        */
/************************************************************************/
static int benefitMth()
{

int i, j;
int cde_error = 0;
int flag = FALSE;
int segment_cnt = 0;
int elig_seg_cnt = 0;
int temp_dte_eff;
int temp_dte_end;
/*action variables:*/
int extend_to_1st_index;
int insert_count;
int insert_eff[6];
int insert_end[6];
int insert_hist[6];
int insert_case[6];
int copy_after_count;
int copy_after_index[2];
int copy_before_count;
int copy_before_index[2];
int update_flag;
int update_index;
int update_eff;
int update_end;
int update_aid_eff;
int update_aid_end;
int replace_aid_flag;
int replace_aid_index = 0;
int extend_flag;
int extend_aid_index = 0; 
int error_flag;
int lower_elig_error_flag;
int temp_ksc_num_rank;
is_future_month   = FALSE;   
was_hw            = 'n';
was_wh            = 'n';

    /*write an error if the case status is D, but no date of death */
    if (case_cde_status[0] == 'D' && dte_death == 0)
    {
       fprintf (stderr, "ERROR: t_re_case_xref.cde_status = D AND t_re_base.dte_death = 0\n");
       fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);

       memcpy(name_field, "DTE DEATH      ", sizeof(name_field));
       memcpy(desc_field, "D STATUS NO DOD", sizeof(desc_field));
       cde_error = 2139;

       if (insertErrTbl(cde_error) != 0)
       {
          fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
          return EXIT_FAILURE;
       }

       end_process = TRUE;
       return EXIT_SUCCESS;
    }

    if (dte_death != 0 && benefit_month_yyyymmdd > dte_death)
    {
       /*bene is deceased and elig rec is for month after they became deceased */
       memcpy(name_field, "DTE DEATH      ", sizeof(name_field));
       memcpy(desc_field, "ELIG AFTER DOD ", sizeof(desc_field));
       cde_error = 2139;

       if (insertErrTbl(cde_error) != 0)
       {
          fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
          return EXIT_FAILURE;
       }

       end_process = TRUE;
       return EXIT_SUCCESS;
    }

    if (declareElig() != 0)
    {
       fprintf (stderr, "ERROR: bad return from declareElig\n");
       return EXIT_FAILURE;
    }

    if (fetchElig() != 0)
    {
       fprintf (stderr, "ERROR: bad return from fetchElig\n");
       return EXIT_FAILURE;
    }

    elig_seg_cnt = sqlca.sqlerrd[2];

    if ((sqlca.sqlcode == 100) && (sqlca.sqlerrd[2] == 0))
    {
       /*no existing elig recs returned, so insert new elig*/

       elig_sak_recip = sak_recip;
       temp_dte_eff = benefit_month_yyyymmdd;
       temp_dte_end = benefit_month_lastday;

       /*If the new elig is a T21 without retro flag,*/
       /*and the benefit month is current month     */
       /*effective date is parm date plus 1*/
       if ((sak_pub_hlth[0] == 4 && retro_t21[0] != 'Y')&&
           (benefit_month_yyyymm == curr_dte_yyyymm))
       {
          benefit_month_yyyymmdd = parm_dte_pls1;
          id_card_benefit_start = parm_dte_pls1;
       }


       /* don't create elig recs that start after date of death*/
       if (dte_death == 0 || dte_death > benefit_month_yyyymmdd)
       {
          /* if deceased, end current month elig with date of death*/
          if (dte_death != 0 && benefit_month_lastday > dte_death)
             benefit_month_lastday = dte_death;

          if (insertElig(0) != 0)
          {
             fprintf(stderr, "ERROR: bad return from insertElig\n");
             return EXIT_FAILURE;
          }

          if (validateTransportLevel() != 0)
          {
             fprintf (stderr, "ERROR: bad return from validateTransportLevel\n");
             return EXIT_FAILURE;
          }
/* 11961 */ /* 14538 - Rolled back 11961*/
       }

       benefit_month_yyyymmdd = temp_dte_eff;
       benefit_month_lastday = temp_dte_end;
    }
    else if (sqlca.sqlerrd[2] != 0)
    {
         rankMN(elig_seg_cnt);    /* rank MN benefit plan to 10 or 120 ? */

         if (benefit_month_yyyymmdd > curr_dte)
            is_future_month = TRUE;

         lower_elig_error_flag = FALSE;
         /*save major num rank*/
         /*(checkMultiPlan checks this variable, so it will be set */
         /* to the dual's num_rank when processing the dual.  it will*/
         /* be reset to the major's num_rank later*/
         temp_ksc_num_rank = ksc_num_rank;

         for (j=0; ( (sak_pub_hlth[j] != 0) && (sak_pub_hlth[j] != '\0') ); j++)
         {
           /* Only insert record if valid spenddown OR invalid spenddown but */
           /* eligibility is not a spenddown health program (QMB and MN, QMB */
           /* record would be inserted but invalid MN would not).            */
           if (bValidSpendRec  == TRUE  ||
              (bValidSpendRec  == FALSE       &&
               sak_pub_hlth[j] != HLTH_MN     &&
               sak_pub_hlth[j] != HLTH_SOBRA  &&
               sak_pub_hlth[j] != HLTH_INMAT))
           {
          /*if a lower elig error occured, don't process any more input recs */
          if (lower_elig_error_flag == TRUE)
             break;

          /* reset action flags */
          error_flag = FALSE;
          insert_count = 0;
          copy_after_count = 0;
          copy_before_count = 0;
          update_flag = FALSE;
          replace_aid_flag = FALSE;
          extend_flag = FALSE;
          extend_to_1st_index = -1;

          segment_cnt = 0;

          for (i=0; i < elig_seg_cnt; i++)
          {
             insert_case[insert_count] = elig_sak_case[i];
             /*if record has been historied, skip it*/
             if (elig_cde_status1[i][0] == 'H')
                 continue;

             if (id_card_printed == FALSE)
             {
                if ((benefit_month_yyyymmdd > elig_dte_end[i]) ||
                    (benefit_month_lastday < elig_dte_eff[i]))
                   id_card_printed = FALSE;
                else
                   id_card_printed = TRUE;
             }

             if ((is_future_month == FALSE) &&
                 (benefit_month_yyyymmdd > elig_dte_end[i]) &&
                 (benefit_month_yyyymmdd > elig_dte_eff[i]))
                 bMidMonthElig = TRUE;
             else
                 bMidMonthElig = FALSE;

             /* if the new and current elig recs can co-exist, just insert */
             /* (don't bother checking if they can co-exist, if thier */
             /*  date ranges don't overlap) */
             if (elig_dte_end[i] >= benefit_month_yyyymmdd &&
                 elig_dte_eff[i] <= benefit_month_lastday)
             {
                
                /* if p19/p21 received, other eligibility exists in MMIS, then
                   dont bother checking, error off PE record with OTHER EXISTS */
                /* P19 is higher in hierarchy than p21, still we want to error off p19 incase p21 is in MMIS*/
                if (( (((sak_pub_hlth[j] == p19_sak_pub_hlth) || (sak_pub_hlth[j] == p21_sak_pub_hlth)) &&
                      ((elig_sak_pub_hlth[i] != p19_sak_pub_hlth) && (elig_sak_pub_hlth[i] != p21_sak_pub_hlth))) ||
                      ((sak_pub_hlth[j] == p19_sak_pub_hlth) && (elig_sak_pub_hlth[i] == p21_sak_pub_hlth)) ) && 
                      (override_bp_hier == FALSE) )
                {
                   flag = FALSE;
                   segment_cnt++;

                   memcpy(name_field, "BENEFIT PLAN   ", sizeof(name_field));
                   memcpy(desc_field, "OTHER EXISTS   ", sizeof(desc_field));
                   bLowerElig = TRUE;
                   cde_error = 2164;
                   /* other elig exists, new PE record cannot win */
                   if (insertPeRevTxn(ksc_case_number,
                                      benefit_month_yyyymm,
                                      sak_pub_hlth[0],
                                      benefit_month_yyyymmdd,
                                      "N",
                                      "2169") != 0)
                   {
                      fprintf (stderr, "ERROR: bad return from insertPeRevTxn for OTHER EXISTS\n");
                      return EXIT_FAILURE;
                   }
                   if (insertErrTbl(cde_error) != 0)
                   {
                       fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
                       return EXIT_FAILURE;
                   }
               
                   error_flag = TRUE;
                   lower_elig_error_flag = TRUE;
                   break;
                }
 
                if (checkCanCoexist(&flag, sak_pub_hlth[j], elig_sak_pub_hlth[i]) != 0)
                {
                   fprintf(stderr, "ERROR: bad return from checkCanCoexist\n");
                   return EXIT_FAILURE;
                }

                if (flag == TRUE)
                {
                   /*(if no other action gets specified, an insert will be done)*/
                   continue;
                }
             }

             if (elig_sak_pub_hlth[i] != sak_pub_hlth[j])
             {
                if (benefit_month_yyyymmdd <= elig_dte_end[i] &&
                    benefit_month_lastday >= elig_dte_eff[i])
                {
                   if (checkMultiPlan(&flag,
                                      &segment_cnt,
                                      elig_num_rank[i],
                                      elig_pop_code[i],
                                      elig_legal_status[i],
                                      elig_cash_prog[i],
                                      elig_cash_prog_sb[i],
                                      elig_src_fund[i],
                                      elig_med_elig_ind[i],
                                      elig_med_subtype[i],
                                      elig_prog_type[i],
                                      elig_prog_subtype[i],
                                      elig_citizen[i],
                                      elig_qmb_ind[i],
                                      elig_epsdt_age[i],
                                      elig_pres_dis_ind[i]) != 0)
                   {
                      fprintf(stderr, "ERROR: bad return from checkMultiPlan\n");
                      return EXIT_FAILURE;
                   }

                   if (flag == TRUE)
                   {
                      if ((benefit_month_yyyymmdd <= elig_dte_eff[i]) || (benefit_month_yyyymm <= elig_dte_eff[i] / 100)) 
                      {
                         if (benefit_month_lastday >= elig_dte_end[i])
                         {
                            /*same date range; create new and history existing*/

                            /*If the new elig is a T21 without retro flag,*/
                            /*effective date is parm date plus 1*/
                            if (is_future_month == FALSE &&
                                sak_pub_hlth[0] == 4 && retro_t21[0] != 'Y')
                            {
                               insert_eff[insert_count] = parm_dte_pls1;
                               id_card_benefit_start = parm_dte_pls1;
                            }
                            else
                               insert_eff[insert_count] = benefit_month_yyyymmdd;

                            insert_end[insert_count] = benefit_month_lastday;
                            insert_hist[insert_count] = i;
                            insert_count++;

                            segment_cnt++;
                         }
                         else /*if (benefit_month_lastday < elig_dte_end[i])*/
                         {
                            /*existing elig finishes later than new one;*/
                            /*insert new one */
                            /*insert one to start after new one*/
                            /*history existing one*/

                            if (copy_after_count == 0 ||
                                elig_sak_pgm_elig[copy_after_index[copy_after_count-1]]
                                    != elig_sak_pgm_elig[i]    )
                            {
                               /*either this is the 1st copy_after OR */
                               /*this is at least a copy_after for a different */
                               /*elig (e.g. was for a major, now for a dual) */

                               /*If the new elig is a T21 without retro flag,*/
                               /*effective date is parm date plus 1*/
                               if (is_future_month == FALSE &&
                                   sak_pub_hlth[0] == 4 && retro_t21[0] != 'Y')
                               {
                                  insert_eff[insert_count] = parm_dte_pls1;
                                  id_card_benefit_start = parm_dte_pls1;
                               }
                               else
                                  insert_eff[insert_count] = benefit_month_yyyymmdd;

                               insert_end[insert_count] = benefit_month_lastday;
                               insert_hist[insert_count] = i;
                               insert_count++;

                               copy_after_index[copy_after_count] = i;
                               copy_after_count++;
                            }

                            segment_cnt++;
                         }
                      }
                      else /*if (benefit_month_yyyymmdd > elig_dte_eff[i])*/
                      {
                         if (benefit_month_lastday >= elig_dte_end[i])
                         {
                            /*new elig is at the end of current elig;*/
                            /*insert new elig with new BP;*/
                            /*insert elig before new one with existing BP*/
                            /*history current*/

                            if (copy_before_count == 0 ||
                                elig_sak_pgm_elig[copy_before_index[copy_before_count-1]]
                                    != elig_sak_pgm_elig[i]    )
                            {
                               /*either this is the 1st copy_before OR */
                               /*this is at least a copy_before for a different */
                               /*elig (e.g. was for a major, now for a dual) */

                               /*If the new elig is a T21 without retro flag,*/
                               /*effective date is parm date plus 1*/
                               if (is_future_month == FALSE &&
                                   sak_pub_hlth[0] == 4 && retro_t21[0] != 'Y')
                               {
                                  insert_eff[insert_count] = parm_dte_pls1;
                                  id_card_benefit_start = parm_dte_pls1;
                               }
                               else
                                  insert_eff[insert_count] = benefit_month_yyyymmdd;

                               insert_end[insert_count] = benefit_month_lastday;
                               insert_hist[insert_count] = i;
                               insert_count++;

                               copy_before_index[copy_before_count] = i;
                               copy_before_count++;
                            }

                            segment_cnt++;
                         }
                         else /*if (benefit_month_lastday < elig_dte_end[i])*/
                         {
                            /*new elig is in the middle of current elig;*/
                            /*insert new elig with new BP;*/
                            /*insert an elig after new one with existing BP;*/
                            /*insert elig before new one with existing BP*/
                            /*history current*/

                            if (copy_after_count == 0 ||
                                elig_sak_pgm_elig[copy_after_index[copy_after_count-1]]
                                    != elig_sak_pgm_elig[i]    )
                            {
                               /*either this is the 1st copy_after OR */
                               /*this is at least a copy_after for a different */
                               /*elig (e.g. was for a major, now for a dual) */

                               /*If the new elig is a T21 without retro flag,*/
                               /*effective date is parm date plus 1*/
                               if (is_future_month == FALSE &&
                                   sak_pub_hlth[0] == 4 && retro_t21[0] != 'Y')
                               {
                                  insert_eff[insert_count] = parm_dte_pls1;
                                  id_card_benefit_start = parm_dte_pls1;
                               }
                               else
                                  insert_eff[insert_count] = benefit_month_yyyymmdd;

                               insert_end[insert_count] = benefit_month_lastday;
                               insert_hist[insert_count] = i;
                               insert_count++;

                               copy_before_index[copy_before_count] = i;
                               copy_before_count++;

                               copy_after_index[copy_after_count] = i;
                               copy_after_count++;
                            }

                            segment_cnt++;
                          }
                       }
                      
                       if (((elig_sak_pub_hlth[i] == p19_sak_pub_hlth) || (elig_sak_pub_hlth[i] == p21_sak_pub_hlth)) &&
                           (override_bp_hier == FALSE)) 
                       {
                           memcpy(desc_field, "BETTER ELIG    ", sizeof(desc_field));

                           /* p19/p21 exists , new record is better than existing */
                           if (insertPeRevTxn(case_number,
                                              benefit_month_yyyymm,
                                              elig_sak_pub_hlth[i],
                                              elig_dte_eff[i],
                                              "N",
                                              "2166") != 0)
                           {
                              fprintf (stderr, "ERROR: bad return from insertPeRevTxn for Better Elig\n");
                              return EXIT_FAILURE;
                           }
                       }
                    }
                    else
                    {
                       /*equal or lesser error in multiplan*/
                       error_flag = TRUE;
                       lower_elig_error_flag = TRUE;
                       break;
                    }
                }
             }
             else if ( ( benefit_month_yyyymmdd <= elig_dte_end[i] &&
                         benefit_month_lastday >= elig_dte_eff[i]     ) &&
                       ( benefit_month_yyyymmdd > aid_elig_dte_end[i] ||
                         benefit_month_lastday < aid_elig_dte_eff[i]     ) )
             {
                /*if same sak_pub_hlth, */
                /* and elig overlaps bene month, */
                /* but this aid elig doesn't overlap bene month, */
                /* ignore this aid elig (only care about the aid elig it overlaps) */
                continue;
             }
             else if (memcmp(elig_pop_code[i], pop_code, sizeof(pop_code)) != 0 &&
                      benefit_month_yyyymmdd <= elig_dte_end[i] &&
                      benefit_month_lastday >= elig_dte_eff[i] )
             {
                /* same sak_pub_hlth */
                /* different pop codes */
                /* some overlap between transaction and existing elig */

                if (checkMultiPlan(&flag,
                                   &segment_cnt,
                                   elig_num_rank[i],
                                   elig_pop_code[i],
                                   elig_legal_status[i],
                                   elig_cash_prog[i],
                                   elig_cash_prog_sb[i],
                                   elig_src_fund[i],
                                   elig_med_elig_ind[i],
                                   elig_med_subtype[i],
                                   elig_prog_type[i],
                                   elig_prog_subtype[i],
                                   elig_citizen[i],
                                   elig_qmb_ind[i],
                                   elig_epsdt_age[i],
                                   elig_pres_dis_ind[i]) != 0)
                {
                   fprintf(stderr, "ERROR: bad return from checkMultiPlan\n");
                   return EXIT_FAILURE;
                }

                if (flag == TRUE)
                {
                   if ((benefit_month_yyyymmdd <= elig_dte_eff[i]) || (benefit_month_yyyymm <= elig_dte_eff[i] / 100))
                   {
                      if (benefit_month_lastday >= elig_dte_end[i])
                      {
                         /*same date range; insert new elig and */
                         /*history existing elig*/

                         insert_eff[insert_count] = benefit_month_yyyymmdd;
                         insert_end[insert_count] = benefit_month_lastday;
                         insert_hist[insert_count] = i;

                         /*If the new elig is a T21 without retro flag,*/
                         /*effective date is one already out there.                 CO11869  */ 
                         if (is_future_month == FALSE &&
                             sak_pub_hlth[0] == 4 && retro_t21[0] != 'Y')
                         {
                            insert_eff [insert_count] = elig_dte_eff [i];
                            id_card_benefit_start     = elig_dte_eff [i];
                         }
                         insert_count++;

                         segment_cnt++;
                      }
                      else /*if (benefit_month_lastday < elig_dte_end[i])*/
                      {
                         /*existing elig finishes later than new elig;*/
                         /* CO11869b CO11869c back out...
                         idayofmon = elig_dte_eff [i] % 100;

                         if (is_future_month == FALSE && idayofmon > 1
                         &&  sak_pub_hlth[0] == 4     && retro_t21[0] != 'Y')
                         {
                               / * CO11869b     Do this because month-end has run.   * / 
                            insert_eff  [insert_count] = elig_dte_eff [i];
                            id_card_benefit_start      = elig_dte_eff [i];
                            insert_end  [insert_count] = benefit_month_lastday;
                            insert_hist [insert_count] = i;
                            insert_count++;
                         }    */
                         replace_aid_flag  = TRUE;    
                         replace_aid_index = i;
                         segment_cnt++;
                      }
                   }
                   else /*if (benefit_month_yyyymmdd > elig_dte_eff[i])*/
                   {
                      if (benefit_month_lastday >= elig_dte_end[i])
                      {
                         /*new elig is at the end of current elig;*/

                         replace_aid_flag = TRUE;
                         replace_aid_index = i;

                         segment_cnt++;
                      }
                      else /*if (benefit_month_lastday < elig_dte_end[i])*/
                      {
                         /*new elig is in the middle of current elig;*/

                         replace_aid_flag = TRUE;
                         replace_aid_index = i;

                         segment_cnt++;
                       }
                    }
                }
                else
                {
                   /*equal or lesser error in multiplan*/
                   error_flag = TRUE;
                   lower_elig_error_flag = TRUE;
                   break;
                }
            }
            else if (benefit_month_min1day == elig_dte_end[i])
            {
               /*new elig is right after existing elig with same plan;*/
               /* for future end date, update to extend to end of new benefit month*/
               /* else, history and insert with new end date*/
               if(elig_dte_end[i] > curr_dte)
               {
                  update_flag = TRUE;
                  update_index = i;
                  update_eff = elig_dte_eff[i];
                  update_end = benefit_month_lastday;
                  update_aid_eff = aid_elig_dte_eff[i];
                  update_aid_end = benefit_month_lastday;
               }
               else
               {
                  extend_flag = TRUE;
                  extend_aid_index = i;
               }

               segment_cnt++;
            }
            else if (benefit_month_lastday_pls1 == elig_dte_eff[i])  /* def 3723 - jcw */
            {
               /*new elig is right before existing elig with same plan;*/
               /*history current elig and insert new one extended to current elig end*/

               /*If the new elig is a T21 without retro flag,*/
               /*effective date is parm date plus 1*/
               if (is_future_month == FALSE &&
                   sak_pub_hlth[0] == 4 && retro_t21[0] != 'Y')
               {
                  insert_eff[insert_count] = parm_dte_pls1;
                  id_card_benefit_start    = parm_dte_pls1;
               }
               else
                  insert_eff[insert_count] = benefit_month_yyyymmdd;

               if (memcmp(elig_pop_code[i], pop_code, sizeof(pop_code)) == 0 )
               {
                  insert_end [insert_count] = elig_dte_end[i];
                  insert_hist[insert_count] = i;
               }
               else
               {
                  /* co10316. The kaecses data is before current elig and has a different pop code.*/ 
                  /*          Do not allow it to override the current elig.                        */   
                  insert_end [insert_count] = benefit_month_lastday;
                  insert_hist[insert_count] = -1;     
               }
               insert_count++;

               segment_cnt++;
            }
            else if ( ( is_future_month == TRUE ||
                        sak_pub_hlth[0] != 4 || retro_t21[0] == 'Y' ) &&
                      ( benefit_month_lastday > elig_dte_end[i] ||
                        benefit_month_yyyymmdd < elig_dte_eff[i] )         )
            {
               /*if retro modifications okay AND */
               /*existing elig does not begin at 1st of month */
               /* (e.g. T21 beginning at parm date + 1) OR */
               /*does not end at end of month */
               /* (e.g. death recorded mid-month in error), */
               /*then extend existing elig */

               if (elig_dte_eff[i] < benefit_month_yyyymmdd)
                  /*existing begins before benefit month*/
                  insert_eff[insert_count] = elig_dte_eff[i];
               else
               {
                  if (elig_dte_eff[i] > benefit_month_yyyymmdd)
                     /*existing begins mid-month*/
                     extend_to_1st_index = i;

                  insert_eff[insert_count] = benefit_month_yyyymmdd;
               }

               if (elig_dte_end[i] > benefit_month_lastday)
                  /*existing ends after benefit month*/
                  insert_end[insert_count] = elig_dte_end[i];
               else
                  /*existing ends mid-month*/
                  insert_end[insert_count] = benefit_month_lastday;

               insert_hist[insert_count] = i;
               insert_count++;

               segment_cnt++;
            }
            else
            {
               /*the only case left: new and existing are same BP */
               /* and they overlap */
               /* and the existing one wasn't extended due to */
               /*     beginning or ending mid-month */
               /*therefore, write equal elig error */

               segment_cnt++;

               /* only write the equal elig error if its the last */
               /* input record */
               if (sak_pub_hlth[j+1] == 0 || sak_pub_hlth[j+1] == '\0')
               {
                  memcpy(name_field, "NUM RANK       ", sizeof(name_field));
                  memcpy(desc_field, "EQUAL ELIG     ", sizeof(desc_field));
                  bEqualElig = TRUE;
                  cde_error = 2010;

                  if (insertErrTbl(cde_error) != 0)
                  {
                     fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
                     return EXIT_FAILURE;
                  }
               }

               /*don't process any more elig recs after equal elig found*/
               error_flag = TRUE;
               break;
            }
          }


         if (error_flag == FALSE &&
             insert_count == 0 &&
             copy_after_count == 0 &&
             copy_before_count == 0 &&
             update_flag == FALSE &&
             replace_aid_flag == FALSE &&
             extend_flag == FALSE)
         {
            /*didn't fit any other situation, so insert new elig*/

            /*If the new elig is a T21 without retro flag,*/
            /*and benefit month is current month  */
            /*effective date is parm date plus 1*/
            if ((sak_pub_hlth[0] == 4 && retro_t21[0] != 'Y') &&
                (benefit_month_yyyymm == curr_dte_yyyymm))
            {
               insert_eff[insert_count] = parm_dte_pls1;
               id_card_benefit_start = parm_dte_pls1;
            }
            else
               insert_eff[insert_count] = benefit_month_yyyymmdd;

            insert_end[insert_count] = benefit_month_lastday;
            insert_hist[insert_count] = -1;
            insert_count++;
         }

         /* check/process action variables */
         if (error_flag == FALSE)
         {
            /* save original values of globals */
            temp_dte_eff = benefit_month_yyyymmdd;
            temp_dte_end = benefit_month_lastday;

                                                                       /*replace aid elig*/
            if (replace_aid_flag == TRUE)
            {
                                                                       /* co11847 prembill */ 
               if ((memcmp (elig_prog_type    [replace_aid_index], "MS", 2) == 0) 
                      &&
                  ((memcmp (elig_med_elig_ind [replace_aid_index], "WH", 2) == 0)  ||
                   (memcmp (elig_med_elig_ind [replace_aid_index], "WL", 2) == 0)  ||
                   (memcmp (elig_med_elig_ind [replace_aid_index], "WM", 2) == 0)  ||
                   (memcmp (elig_med_elig_ind [replace_aid_index], "WQ", 2) == 0)  ||
                   (memcmp (elig_pres_dis_ind [replace_aid_index], "WH", 2) == 0)) 
                      &&
                  ((memcmp (elig_pop_code     [replace_aid_index], "26", 2) == 0)    ||
                   (memcmp (elig_pop_code     [replace_aid_index], "27", 2) == 0))
                      &&
                   (elig_dte_eff              [replace_aid_index] < benefit_month_lastday)
                      &&
                   (elig_dte_end              [replace_aid_index] > benefit_month_yyyymmdd)) 
               {
                  was_wh = 'y'; 
               }
            }

            if ((elig_sak_pub_hlth [replace_aid_index] == 4)
                   &&
                (elig_dte_eff      [replace_aid_index] < benefit_month_lastday)
                   &&
                (elig_dte_end      [replace_aid_index] > benefit_month_yyyymmdd)) 
            {
               was_hw = 'y'; 
/* CO11847 HealthWave shut off. */ 
/*             was_hw = 'n';    */
            }

            /*replace aid elig*/
            if (replace_aid_flag == TRUE)
            {
               updat_aid_flag = TRUE;

               if (benefit_month_yyyymmdd < dte_death && benefit_month_lastday > dte_death)
                  benefit_month_lastday = dte_death;

               if (replaceAidElig(replace_aid_index, j) != 0)
               {
                  fprintf(stderr, "ERROR: bad return from replaceAidElig\n");
                  return EXIT_FAILURE;
               }

               /*make sure no other actions are taken*/
               extend_flag = FALSE;
               insert_count = 0;
               copy_after_count = 0;
               copy_before_count = 0;
               update_flag = FALSE;
            }

            /*extend elig*/
            if (extend_flag == TRUE)
            {
               if (benefit_month_yyyymmdd < dte_death && benefit_month_lastday > dte_death)
                  benefit_month_lastday = dte_death;

               elig_sak_recip = sak_recip;

               if (extendElig(extend_aid_index) != 0)
               {
                  fprintf(stderr, "ERROR: bad return from extendElig\n");
                  return EXIT_FAILURE;
               }

               /*make sure some other actions aren't taken*/
               /*(copy_after's and history'ing of insert's still done*/
               /* when replacing something after what's being extended)*/
               copy_before_count = 0;
               update_flag = FALSE;

               /*special case: extend to right & same BP extend to left where next elig starts mid-month*/
               /*(Will basically treat this like a replace or partial replace of a different BP elig*/
               /* without the insert, since the extend to right will take care of that.)*/
               if (extend_to_1st_index != -1)
               {
                   insert_count = 0;
                   copy_after_count = 0;
                   if (benefit_month_lastday >= elig_dte_end[extend_to_1st_index])
                   {
                      /*same date range; create new and history existing*/
                      insert_eff[insert_count] = 0;
                      insert_end[insert_count] = 0;
                      insert_case[insert_count] = 0;
                      insert_hist[insert_count] = extend_to_1st_index;
                      insert_count++;
                   }
                   else /*if (benefit_month_lastday < elig_dte_end[i])*/
                   {
                      /*existing elig finishes later than new one;*/
                      /*insert one to start after new one*/
                      /*history existing one*/

                      insert_eff[insert_count] = 0;
                      insert_end[insert_count] = 0;
                      insert_case[insert_count] = 0;
                      insert_hist[insert_count] = extend_to_1st_index;
                      insert_count++;

                      copy_after_index[copy_after_count] = extend_to_1st_index;
                      copy_after_count++;
                   }
               }
            }

            /* copy after */
            /* (insert copying values from an existing record and */
            /*  starting at end of the new elig record)*/
            /* there could be multiple copy situations found */
            /* (i.e. copy for a major and copy for a dual ) */
            for (i=0; i < copy_after_count; i++)
            {
               elig_sak_recip = sak_recip;
               benefit_month_yyyymmdd = benefit_month_lastday_pls1;
               benefit_month_lastday = elig_dte_end[copy_after_index[i]];

               /* don't create elig recs that start after date of death*/
               if (dte_death == 0 || dte_death > benefit_month_yyyymmdd)
               {
                  /* if deceased, end current month elig with date of death*/
                  if (dte_death != 0 && benefit_month_lastday > dte_death)
                     benefit_month_lastday = dte_death;

                  if (copyElig(elig_sak_pub_hlth[copy_after_index[i]], elig_num_rank[copy_after_index[i]], copy_after_index[i]) != 0)
                  {
                     fprintf(stderr, "ERROR: bad return from copyElig\n");
                     return EXIT_FAILURE;
                  }
               }
            }

            /* copy before */
            /* (insert copying values from an existing record and */
            /*  ending with the beginning of the new elig record)*/
            /* there could be multiple copy situations found */
            /* (i.e. copy for a major and copy for a dual ) */
            for (i=0; i < copy_before_count; i++)
            {
               elig_sak_recip = sak_recip;
               benefit_month_yyyymmdd = elig_dte_eff[copy_before_index[i]];
               benefit_month_lastday = benefit_month_min1day;

               /* don't create elig recs that start after date of death*/
               if (dte_death == 0 || dte_death > benefit_month_yyyymmdd)
               {
                  /* if deceased, end current month elig with date of death*/
                  if (dte_death != 0 && benefit_month_lastday > dte_death)
                     benefit_month_lastday = dte_death;

                  if (copyElig(elig_sak_pub_hlth[copy_before_index[i]], elig_num_rank[copy_before_index[i]], copy_before_index[i]) != 0)
                  {
                     fprintf(stderr, "ERROR: bad return from copyElig\n");
                     return EXIT_FAILURE;
                  }
               }
            }

            /* insert */
            if (insert_count > 0)
            {
               /* there could be multiple insert situations found */
               /* (i.e. any combination of: */
               /*        - inserting a new elig */
               /*        - extending before an existing elig */
               /*        - extending after an existing elig ) */
               /* one insert will be done from the least effective */
               /* date to the greatest end date */
               for (i=0; i < insert_count; i++)
               {
                  if (insert_eff[i] < insert_eff[0])
                     insert_eff[0] = insert_eff[i];

                  if (insert_end[i] > insert_end[0])
                     insert_end[0] = insert_end[i];

                  /* some will have an associated history action */
                  if (insert_hist[i] > -1)
                  {
                     /* if an extend to right and insert from extend to left, */
                     /* don't history even history extend to left*/
                     if (extend_flag != TRUE ||
                         elig_dte_eff[insert_hist[i]] != benefit_month_lastday_pls1)
                     {
                         if (historyElig(elig_sak_pgm_elig[insert_hist[i]]) != 0)
                         {
                            fprintf(stderr, "ERROR: bad return from historyElig\n");
                            return EXIT_FAILURE;
                         }

                         /*note that this elig rec has been historied*/
                         /*(so processing of next input record skips it)*/
                         elig_cde_status1[insert_hist[i]][0] = 'H';
                     }
                  }
               }

               /*don't do the actual insert here if an extend was done*/
               /*(just history the recs above)*/
               if (extend_flag == FALSE)
               {
                  elig_sak_recip = sak_recip;
                  benefit_month_yyyymmdd = insert_eff[0];
                  benefit_month_lastday = insert_end[0];
                  benefit_case = insert_case[0];

                  /* if insert overlaps update, skip update */
                  /* (an extend on a same sak_pub_hlth and different pop code AND */
                  /*  and extend on a same sak_pub_htlh and same pop code ) */
                  if (update_flag == TRUE &&
                      (insert_eff[0] <= update_end &&
                       insert_end[0] >= update_eff    ) )
                  {
                     if (insert_eff[0] < update_eff)
                     {
                         update_eff = insert_eff[0];
                         update_aid_eff = insert_eff[0];
                     }
                     if (insert_end[0] > update_end)
                     {
                         update_end = insert_end[0];
                         update_aid_end = insert_end[0];
                     }
                  }
                  else
                  {
                     /* don't create elig recs that start after date of death*/
                     if (dte_death == 0 || dte_death > benefit_month_yyyymmdd)
                     {
                        /* if deceased, end current month elig with date of death*/
                        if (dte_death != 0 && benefit_month_lastday > dte_death)
                           benefit_month_lastday = dte_death;

                        if (insertOneElig(j) != 0)
                        {
                           fprintf(stderr, "ERROR: bad return from insertOneElig\n");
                           return EXIT_FAILURE;
                        }
                     }
                  }
               }
            }

            /* reset globals */
            /* (used in update call) */
            benefit_month_yyyymmdd = temp_dte_eff;
            benefit_month_lastday = temp_dte_end;

            /* update */
            if (update_flag == TRUE)
            {
               /* don't create elig recs that start after date of death*/
               if (dte_death == 0 || dte_death > update_eff)
               {
                  /* if deceased, end current month elig with date of death*/
                  if (dte_death != 0 && update_end > dte_death)
                  {
                     update_end = dte_death;
                     update_aid_end = dte_death;
                  }

                  if (updateEligSeg(update_eff, update_end, elig_sak_pgm_elig[update_index], benefit_month_yyyymmdd,
                      update_aid_eff, update_aid_end, update_index) != 0)
                  {
                     fprintf(stderr, "ERROR: bad return from updateEligSeg\n");
                     return EXIT_FAILURE;
                  }
               }
            }
         }
         /*moving to dual benefit plan*/
         ksc_num_rank = ksc_2nd_num_rank;
         }
        }
        /*reset variable back to major benefit plan*/
        ksc_num_rank = temp_ksc_num_rank;
      }

      sqlca.sqlcode = 0;
      EXEC SQL
        CLOSE ELIGCSR;

       if (sqlca.sqlcode != 0)
       {
          fprintf(stderr, "ERROR: could not close ELIGCSR\n");
          fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   checkCanCoexist()                                  */
/*                                                                      */
/*  Description:    Determines if two benefit plans can co-exist during */
/*                  the same time period.                               */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   09/12/2003 James Mercer      Initial Release               */
/************************************************************************/
static int checkCanCoexist(int *flag, int in_sak1, int in_sak2)
{
exec sql begin declare section;
int sak_pub_hlth1;
int sak_pub_hlth2;
int num_recs;
exec sql end declare section;

   sak_pub_hlth1 = in_sak1;
   sak_pub_hlth2 = in_sak2;
   num_recs = 0;

   sqlca.sqlcode = 0;

   EXEC SQL
     SELECT count(sak_pub_hlth)
     INTO  :num_recs
     FROM  t_pgm_exclusion
     WHERE ( sak_pub_hlth = :sak_pub_hlth1 AND
             sak_excluded_pgm = :sak_pub_hlth2 ) OR
           ( sak_pub_hlth = :sak_pub_hlth2 AND
             sak_excluded_pgm = :sak_pub_hlth1 );

   /* If no records are found on the table with both of the */
   /* input sak_pub_hlth's, the sak_pub_hlth's can co-exist.*/

   if (sqlca.sqlcode == 100)
   {  /* no recs returned */
           *flag = TRUE;
           return EXIT_SUCCESS;
   }
   else if (sqlca.sqlcode != 0)
   {
           *flag = FALSE;
           fprintf (stderr, "ERROR: could not select from t_pgm_exclusion table\n");
           fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
           fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
   }
   else if (num_recs > 0)
   {  /* one or more exclusion recs found */
           *flag = FALSE;
           return EXIT_SUCCESS;
   }
   else
   {
           *flag = TRUE;
           return EXIT_SUCCESS;
   }
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   getLatestEligMonth()                               */
/*                                                                      */
/*  Description:    Retrieves the latest elig month for a bene.         */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/26/2004 James Mercer      Initial Release               */
/*  CO6376   04/21/2004 Lisa Salgat       Modified SQL.                 */
/************************************************************************/
static int getLatestEligMonth(int *latest_elig_month)
{
exec sql begin declare section;
int sql_latest_elig_month = 0;
int maxEnd = 0;
exec sql end declare section;

   sqlca.sqlcode = 0;

   EXEC SQL
     SELECT NVL(MAX(ROUND(e.dte_end/100, 0)), 0)
     INTO  :sql_latest_elig_month
     FROM  t_re_elig e,
           t_pub_hlth_pgm p
     WHERE e.sak_recip = :sak_recip
       AND e.cde_status1 = ' '
       AND e.sak_pub_hlth = p.sak_pub_hlth
       AND p.cde_type in ('BO', 'DU', 'MJ', 'SA')
       AND p.cde_pgm_health <> 'PACE'
       AND p.cde_pgm_health <> 'ADAPD'
       AND p.cde_pgm_health <> 'ADAPT';

   if (sqlca.sqlcode != 0)
           *latest_elig_month = 0;
   else
           *latest_elig_month = sql_latest_elig_month;

   if (benefit_month_yyyymm >= *latest_elig_month)
        bActiveElig = TRUE;
   else
        bActiveElig = FALSE;

   bActiveEligMonth = TRUE;

   if (*latest_elig_month == 0)
   {
      EXEC SQL
         SELECT NVL(MAX(ROUND(dte_end/100, 0)), 0)
           INTO :maxEnd
           FROM t_re_elig
          WHERE sak_recip   = :sak_recip
            AND cde_status1 <> 'H'
            AND dte_end     < 22991231;

      if (sqlca.sqlcode != 0)
      {
         fprintf (stderr, "ERROR: bad fetch from t_re_elig for maxEff date\n");
         fprintf (stderr, "ERROR: Sak Recip %d, sqlca.sqlcode %d\n", sak_recip, sqlca.sqlcode);
         return EXIT_FAILURE;
      }
      else
      {
         if (maxEnd > 0 && benefit_month_yyyymm < maxEnd)
            bActiveEligMonth = FALSE;
      }
   }

   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   checkMultiPlan()                                   */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR            DESCRIPTION                   */
/*  -----    ---------  ---------------   ----------------------------- */
/*  CO1305   01/04/2003 Koren Allen       Initial Release               */
/*  CO8831   02/02/2006 Srinivas Donepudi Added PDI logic               */       
/************************************************************************/
static int checkMultiPlan(int *flag,
                          int *segment_count,
                          int rank,
                          char code[],
                          char status[],
                          char cash_prog[],
                          char cash_prog_sb[],
                          char src[],
                          char elig_ind[],
                          char med_sb[],
                          char prog_type[],
                          char prog_sb[],
                          char citizen[],
                          char ind[],
                          int age,
                          char pdi[])

{

int hold_num_rank = 0;
int cde_error = 0;
char new_pop_code[2+1];
char new_legal_status[2+1];
char new_cash_prog[2+1];
char new_cash_prog_sb[2+1];
char new_src_fund[2+1];
char new_med_elig_ind[2+1];
char new_med_subtype[2+1];
char new_prog_type[2+1];
char new_prog_subtype[2+1];
char new_qmb_ind[1+1];
char new_citizen[2+1];
int new_epsdt_age;
char new_pres_dis_ind[2+1];
 
  hold_num_rank = rank;
  memcpy(new_pop_code, code, sizeof(new_pop_code));
  memcpy(new_legal_status, status, sizeof(new_legal_status));
  memcpy(new_cash_prog, cash_prog, sizeof(new_cash_prog));
  memcpy(new_cash_prog_sb, cash_prog_sb, sizeof(new_cash_prog_sb));
  memcpy(new_src_fund, src, sizeof(new_src_fund));
  memcpy(new_med_elig_ind, elig_ind, sizeof(new_med_elig_ind));
  memcpy(new_med_subtype, med_sb, sizeof(new_med_subtype));
  memcpy(new_prog_type, prog_type, sizeof(new_prog_type));
  memcpy(new_prog_subtype, prog_sb, sizeof(new_prog_subtype));
  memcpy(new_citizen, code, sizeof(new_citizen));
  new_qmb_ind[0] = ind[0];
  new_epsdt_age = age;
  memcpy(new_pres_dis_ind, pdi, sizeof(new_pres_dis_ind));

    if (getPopCodes(&new_pop_code,
                    &priority_code,
                    &man_code,
                    &cap_ind,
                    new_legal_status,
                    new_citizen,
                    new_cash_prog,
                    new_cash_prog_sb,
                    new_src_fund,
                    new_med_elig_ind,
                    new_med_subtype,
                    new_prog_type,
                    new_prog_subtype,
                    new_qmb_ind,
                    new_epsdt_age,
                    new_pres_dis_ind) != 0)
    {
       fprintf (stderr, "ERROR: bad return from getPopCodes\n");
       return EXIT_FAILURE;
    }


     if (ksc_num_rank > hold_num_rank)
     {
        *flag = TRUE;
     }
     else if (ksc_num_rank == hold_num_rank)
     {
        if (curr_priority_code < priority_code)
        {
           *flag = TRUE;
        }
        else if (curr_priority_code == priority_code)
        {
           *flag = FALSE;
           (*segment_count)++;

           memcpy(name_field, "NUM RANK       ", sizeof(name_field));
           memcpy(desc_field, "EQUAL ELIG     ", sizeof(desc_field));
           bEqualElig = TRUE;
           cde_error = 2010;

           if (insertErrTbl(cde_error) != 0)
           {
              fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
              return EXIT_FAILURE;
           }
        }
        else
        {
           *flag = FALSE;
           (*segment_count)++;

           memcpy(name_field, "NUM RANK       ", sizeof(name_field));
           memcpy(desc_field, "LOWER BENEFIT  ", sizeof(desc_field));
           bLowerElig = TRUE;
           cde_error = 2008;

           if (insertErrTbl(cde_error) != 0)
           {
              fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
              return EXIT_FAILURE;
           }
        }
     }
     else
     {
       if (override_bp_hier == TRUE)
       {
          *flag = TRUE;
       }
       else
       {
        *flag = FALSE;
        (*segment_count)++;

        memcpy(name_field, "NUM RANK       ", sizeof(name_field));
        memcpy(desc_field, "LOWER BENEFIT  ", sizeof(desc_field));
        bLowerElig = TRUE;
        cde_error = 2008;

        if ((hold_num_rank == p19_num_rank) || (hold_num_rank == p21_num_rank))
        {
          memcpy(desc_field, "LOWER ELIG     ", sizeof(desc_field));
 
          /* p19/p21 exists , new record did not win over existing */
          if (insertPeRevTxn(ksc_case_number,
                             benefit_month_yyyymm,
                             sak_pub_hlth[0],
                             benefit_month_yyyymmdd,
                             (ksc_num_rank==10) ? "Y" : "N",
                             "2165") != 0)
          {
               fprintf (stderr, "ERROR: bad return from insertPeRevTxn for lower elig\n");
               return EXIT_FAILURE;
          }
        }

        if (insertErrTbl(cde_error) != 0)
        {
           fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
           return EXIT_FAILURE;
        }
       }
     }
   
   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   previousInfo()                                     */
/*                                                                      */
/*  Description:     Update bene's demographic information              */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int previousInfo()
{

    if ((memcmp(prev_nam_first, nam_first, sizeof(prev_nam_first)) != 0) ||
        (memcmp(prev_nam_last, nam_last, sizeof(prev_nam_last)) != 0))
    {
       if (updateNameXref() != 0)
       {
          fprintf (stderr, "ERROR: bad return from updateNameXref\n");
	      return EXIT_FAILURE;
       }
    }

    if ((memcmp(prev_adr_street1, base_adr_street1, sizeof(base_adr_street1)-1) != 0) ||
        (memcmp(prev_adr_street2, base_adr_street2, sizeof(base_adr_street2)-1) != 0) ||
        (memcmp(prev_adr_city, base_adr_city, sizeof(base_adr_city)-1) != 0) ||
        (memcmp(prev_adr_state, base_adr_state, sizeof(base_adr_state)-1) != 0) ||
        (memcmp(prev_adr_zip_code, base_adr_zip_code, sizeof(base_adr_zip_code)-1) != 0) ||
        (memcmp(prev_adr_zip_code_4, base_adr_zip_code_4, sizeof(base_adr_zip_code_4)-1) != 0))
    {
       if (updateAddressXref() != 0)
       {
          fprintf (stderr, "ERROR: bad return from updateaddressXref\n");
	  return EXIT_FAILURE;
       }
    }

    if (memcmp(prev_cde_county, base_cde_county, sizeof(base_cde_county)-1) != 0)
    {
       if (updateCountyXref() != 0)
       {
          fprintf (stderr, "ERROR: bad return from updateCountyXref\n");
          return EXIT_FAILURE;
       }
    }

    if (updateRaceXref() != 0)
    {
       fprintf (stderr, "ERROR: bad return from updateRaceXref\n");
       return EXIT_FAILURE;
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateNameXref()                                   */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int updateNameXref()
{

exec sql begin declare section;
int sak_short;
exec sql end declare section;

     sqlca.sqlcode = 0;
     EXEC SQL
       SELECT nvl(max(sak_short_name),0)
       INTO   :sak_short
       FROM   t_re_name_xref
       WHERE sak_recip = :sak_recip;

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not select max sak short from t_re_name_xref\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      sak_short++;

      sqlca.sqlcode = 0;
      EXEC SQL
         INSERT INTO t_re_name_xref
                 (sak_recip,
                  sak_short_name,
                  nam_last,
                  nam_first,
                  nam_mid_init,
                  dte_last_change)
         VALUES  (:sak_recip,
                  :sak_short,
                  :prev_nam_last,
                  :prev_nam_first,
                  :prev_nam_mid,
                  :curr_dte);

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not insert into t_re_name_xref\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateAddressXref()                                */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int updateAddressXref()
{

exec sql begin declare section;
int sak_short;
exec sql end declare section;

     sqlca.sqlcode = 0;
     EXEC SQL
       SELECT nvl(max(sak_short_address),0)
       INTO   :sak_short
       FROM   t_re_address
       WHERE sak_recip = :sak_recip;

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not select max sak short from t_re_address\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      sak_short++;

      sqlca.sqlcode = 0;
      EXEC SQL
         INSERT INTO t_re_address
                 (sak_recip,
                  sak_short_address,
                  adr_street_1,
                  adr_street_2,
                  adr_city,
                  adr_state,
                  adr_zip_code,
                  adr_zip_code_4,
                  num_latitude,
                  num_longitude,
                  cde_gis_quality,
                  dte_last_change)
         VALUES  (:sak_recip,
                  :sak_short,
                  :prev_adr_street1,
                  :prev_adr_street2,
                  :prev_adr_city,
                  :prev_adr_state,
                  :prev_adr_zip_code,
                  :prev_adr_zip_code_4,
                  '0',
                  '0',
                  '0',
                  :curr_dte);

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not insert into t_re_address\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateCountyXref()                                 */
/*                                                                      */
/*  Description:     Insert a new county xref record when the bene's    */
/*                   county changes.                                    */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   12/18/2003 James Mercer      Initial Release               */
/************************************************************************/
static int updateCountyXref()
{

exec sql begin declare section;
int sak_short;
exec sql end declare section;

     sqlca.sqlcode = 0;
     EXEC SQL
       SELECT nvl(max(sak_prev_cnty),0)
       INTO   :sak_short
       FROM   t_re_prev_cty
       WHERE sak_recip = :sak_recip;

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not select max sak short from t_re_prev_cty\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      sak_short++;

      sqlca.sqlcode = 0;
      EXEC SQL
         INSERT INTO t_re_prev_cty
                 (sak_recip,
                  sak_prev_cnty,
                  cde_county,
                  cde_office,
                  dte_last_change)
         VALUES  (:sak_recip,
                  :sak_short,
                  :prev_cde_county,
                  :base_cde_office,
                  :curr_dte);

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not insert into t_re_prev_cty\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateRaceXref()                                   */
/*                                                                      */
/*  Description:     Make the list of race codes on the database, the   */
/*                   same as those on the input file.                   */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/07/2004 James Mercer      Initial Release               */
/************************************************************************/
static int updateRaceXref()
{
exec sql begin declare section;
char db_cde_race[20][1+1];
int db_sak_short[20];
int  db_num_recs;

char insert_cde_race[20];
int  insert_index = -1;
int delete_sak_short[20];
int  delete_index = -1;

int sak_short = 0;
char space = ' ';
char code[1+1];
exec sql end declare section;
int i, j = 0;
int cde_race_found;

   /*get the list of race code on the database */
   sqlca.sqlcode = 0;
   EXEC SQL
      SELECT cde_race,
             sak_short
      INTO   :db_cde_race,
             :db_sak_short
      FROM   t_re_race_xref
      WHERE  sak_recip = :sak_recip;

   db_num_recs = sqlca.sqlerrd[2];

   if (sqlca.sqlcode != 0 && sqlca.sqlcode != 100)
   {
      fprintf(stderr, "ERROR: could not select from t_re_race_xref\n");
      fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
      return EXIT_FAILURE;
   }
   else
   {
      /* spin thru race codes on transaction */
      for(i=0; (race_code[i] != NULL); i++)
      {
         if (race_code[i] != space)
         {
            cde_race_found = FALSE;

            /* see if race code is already on db */
            for(j=0; j < db_num_recs; j++)
            {
               if ( race_code[i] == db_cde_race[j][0] )
               {
                  cde_race_found = TRUE;
                  break;
               }
            }

            /* if not, add to insert array */
            if (cde_race_found == FALSE)
            {
               insert_index++;
               insert_cde_race[insert_index] = race_code[i];
            }
         }
      }

      /* spin thru race codes on db */
      for(j=0; j < db_num_recs; j++)
      {
         /*keep track of greatest sak_short*/
         if (db_sak_short[j] > sak_short)
            sak_short = db_sak_short[j];

         cde_race_found = FALSE;

         /* see if db race code is in transaction */
         for(i=0; (race_code[i] != NULL); i++)
         {
            if ( race_code[i] == db_cde_race[j][0] )
            {
               cde_race_found = TRUE;
               break;
            }
         }

         /* if not, add to delete array */
         if (cde_race_found == FALSE)
         {
            delete_index++;
            delete_sak_short[delete_index] = db_sak_short[j];
         }
      }


      /* make inserts, updates, and deletes from insert/delete arrays... */

      j = 0; /* (we'll use this for the current delete index) */
      /* spin thru insert array */
      for(i=0; i <= insert_index; i++)
      {
         code[0] = insert_cde_race[i];
         code[1]= '\0';

         /* if records to delete */
         if (j <= delete_index)
         {
            /* update the existing record */
            sqlca.sqlcode = 0;
            EXEC SQL
              UPDATE t_re_race_xref
              SET    cde_race = :code
              WHERE  sak_recip = :sak_recip
                 AND sak_short = :delete_sak_short[j];

            if (sqlca.sqlcode != 0)
            {
               fprintf(stderr, "ERROR: could not update race code\n");
               fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
               fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
               return EXIT_FAILURE;
            }

            j++;
         }
         else
         {
            /* insert new record */
            sak_short++;

            sqlca.sqlcode = 0;
            EXEC SQL
              INSERT INTO t_re_race_xref
                         (sak_recip,
                          cde_race,
                          sak_short)
              VALUES  (:sak_recip,
                       :code,
                       :sak_short);

            if (sqlca.sqlcode != 0)
            {
               fprintf(stderr, "ERROR: could not insert race code\n");
               fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
               fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
               return EXIT_FAILURE;
            }
         }
      } /* end of insert array loop */

      /* spin thru rest of delete array */
      for(; j <= delete_index; j++)
      {
         /*delete record*/
         sqlca.sqlcode = 0;
         EXEC SQL
           DELETE t_re_race_xref
           WHERE  sak_recip = :sak_recip
              AND sak_short = :delete_sak_short[j];

         if (sqlca.sqlcode != 0)
         {
            fprintf(stderr, "ERROR: could not delete race code\n");
            fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
            fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
            return EXIT_FAILURE;
         }
      }
   }

   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   checkValidSpend()                                  */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  6576     05/21/2004 Lisa Salgat      Initial Release                */
/*  7534     11/22/2004 Lisa Salgat      Updated to not process         */
/*                                       overlapping rows.              */
/*  7534     10/10/2005 Lisa Salgat      Updated overlap SQL to compare */
/*                                       dates to case_xref dates too.  */
/************************************************************************/
static int checkValidSpend()
{
int bValidSpenddown = FALSE;
int cde_error = 0;
int rc = 0;

exec sql begin declare section;
int tmp_count = 0;
int base_end_ymd;
int base_st_ymd;
int spenddown_eff;
int spenddown_end;
exec sql end declare section;

    bValidSpendRec = TRUE;

    if ((sak_pub_hlth[0] == HLTH_MN    ||
         sak_pub_hlth[0] == HLTH_SOBRA ||
         sak_pub_hlth[0] == HLTH_INMAT ) &&
         base_st_ym > 0)
    {
       sqlca.sqlcode = 0;
       EXEC SQL
         SELECT to_char(last_day(to_date(:base_end_ym, 'YYYYMM')), 'YYYYMMDD'),
                :base_st_ym||'01'
         INTO   :base_end_ymd,
                :base_st_ymd
         FROM   dual;

       if (sqlca.sqlcode != 0)
       {
         fprintf(stderr, "ERROR: could not convert dates\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
       }

       sqlca.sqlcode = 0;
       EXEC SQL
         SELECT a.dte_effective, a.dte_end
           INTO :spenddown_eff,
                :spenddown_end
           FROM  t_re_spend_liab a,
                 t_re_case       b
          WHERE  b.num_case      = :ksc_case_number
            AND  a.sak_case      = b.sak_case
            AND  ((:base_st_ymd  < a.dte_effective
            AND  (:base_end_ymd  BETWEEN a.dte_effective AND a.dte_end
             OR  :base_end_ymd   > a.dte_end))
             OR  (:base_st_ymd   BETWEEN a.dte_effective AND a.dte_end
            AND  :base_st_ymd   != a.dte_effective)
             OR  (:base_st_ymd   = a.dte_effective
            AND  :base_end_ymd   > a.dte_end
            AND  :ksc_spend_amt != a.amt_spenddown))
            AND  rownum = 1;

       if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
       {
          fprintf(stderr, "ERROR: could not select spenddown segment based on case and dates\n");
          fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }
       else if (sqlca.sqlcode == 0)
       {
          if (base_st_ymd > spenddown_eff)
          {
              memcpy(name_field, "BASE ST YM     ", sizeof(name_field));
              memcpy(desc_field, "EFFDTE > SPNEFF", sizeof(desc_field));
              cde_error = 2006;
              bValidSpendRec = FALSE;
          }
          else if (base_st_ymd < spenddown_eff)
          {
             memcpy(name_field, "BENE SAK CASE  ", sizeof(name_field));
             memcpy(desc_field, "SPEND OVERLAP  ", sizeof(desc_field));
             cde_error = 2146;
             bValidSpendRec = FALSE;
          }
          else /* base end > spenddown date end */
          {
             memcpy(name_field, "BASE END YM    ", sizeof(name_field));
             memcpy(desc_field, "ENDDTE > SPNDTE", sizeof(desc_field));
             cde_error = 2005;
             bValidSpendRec = FALSE;
          }
       }
       else
       {
             sqlca.sqlcode  = 0;

             EXEC SQL DECLARE OVERLAP_CHK CURSOR FOR
               SELECT a.dte_effective,
                      a.dte_end 
               FROM   t_re_spend_liab a,
                      t_re_case_xref b,
                      t_re_case c
               WHERE  b.sak_recip  = :sak_recip
                 AND  b.sak_case   = a.sak_case
                 AND  b.sak_case   = c.sak_case
                 AND  c.num_case  != :ksc_case_number
                 AND  ((:base_st_ymd    BETWEEN a.dte_effective AND a.dte_end
                  OR    :base_end_ymd   BETWEEN a.dte_effective AND a.dte_end)
                  OR   (a.dte_effective BETWEEN :base_st_ymd    AND :base_end_ymd
                  OR    a.dte_end       BETWEEN :base_st_ymd    AND :base_end_ymd))
                 AND  ((:base_st_ymd    BETWEEN b.dte_cert      AND b.dte_end
                  OR    :base_end_ymd   BETWEEN b.dte_cert      AND b.dte_end)
                  OR   (b.dte_cert      BETWEEN :base_st_ymd    AND :base_end_ymd
                  OR    b.dte_end       BETWEEN :base_st_ymd    AND :base_end_ymd));

             EXEC SQL OPEN OVERLAP_CHK;

             rc = sqlca.sqlcode;

             while (rc == 0)
             {
                EXEC SQL FETCH OVERLAP_CHK
                INTO :spenddown_eff,
                     :spenddown_end;

                rc = sqlca.sqlcode;

                if (rc == 0)
                {               /* Checking (HLTH_MN, HLTH_SOBRA, HLTH_INMAT) */
                   EXEC SQL
                      SELECT count(*)
                        INTO :tmp_count
                        FROM t_re_elig
                       WHERE sak_recip      = :sak_recip
                         AND sak_pub_hlth  IN (3, 5, 79)
                         AND cde_status1    = ' '
                         AND (:spenddown_eff BETWEEN dte_effective AND dte_end
                          OR :spenddown_end BETWEEN dte_effective AND dte_end);

                   if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
                   {
                      fprintf(stderr, "ERROR: could not select elig via spenddown info\n");
                      fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
                      fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
                      return EXIT_FAILURE;
                   }

                   if (tmp_count > 0)
                   {
                      memcpy(name_field, "BENE SAK CASE  ", sizeof(name_field));
                      memcpy(desc_field, "SPEND OVERLAP  ", sizeof(desc_field));
                      cde_error = 2146;
                      bValidSpendRec = FALSE;
                   }
                }
             }

             if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
             {
                fprintf(stderr, "ERROR: could not select spenddown segment based on beneficiary and dates\n");
                fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
                fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
                return EXIT_FAILURE;
             }

             EXEC SQL CLOSE OVERLAP_CHK;
             if (sqlca.sqlcode != 0)
             {
                fprintf(stderr, "ERROR: could not close OVERLAP_CHK cursor\n");
                fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
                fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
                return EXIT_FAILURE;

             }
       }
    }
/*  .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   . SOBRA start */
    if (sak_pub_hlth[0] == HLTH_SOBRA &&
        bValidSpendRec  == TRUE)
    {
      if (strncmp(case_number, ksc_case_number, sizeof(ksc_case_number)) !=  0)
      {
         if (verifyCaseNum() != 0)
         {
            fprintf (stderr, "ERROR: bad return from verifyCaseNum\n");
            return EXIT_FAILURE;
         }
      }

      sqlca.sqlcode = 0;
      EXEC SQL
         SELECT count(sak_spend_liab)
         INTO   :tmp_count
         FROM   t_re_spend_liab
         WHERE  sak_case = :sak_case
         AND    :benefit_month_yyyymmdd BETWEEN dte_effective AND dte_end;

      if (sqlca.sqlcode == 0)
      {
        if (tmp_count > 0)
        {
          if (base_st_ym == 0 && base_end_ym == 0)
          {
              memcpy(name_field, "SOBRA SAK CASE ", sizeof(name_field));
              memcpy(desc_field, "SPENDDOWN EXIST", sizeof(desc_field));
/*            memcpy(desc_field, base_id, 11); */
              cde_error = 2144;
              bValidSpendRec = FALSE;
           }
           else
              bValidSpenddown = TRUE;
        }

        if (base_st_ym > 0 && base_end_ym > 0 && bValidSpenddown == FALSE)
        {                  /* Checking HLTH_SOBRA */
          sqlca.sqlcode = 0;
          EXEC SQL
            SELECT count(*)
            INTO   :tmp_count
            FROM   t_re_elig
            WHERE  sak_recip    = :sak_recip
            AND    sak_pub_hlth = 5
            AND    cde_status1  = ' '
            AND   (:base_st_ymd  BETWEEN dte_effective AND dte_end
            OR     :base_end_ymd BETWEEN dte_effective AND dte_end
            OR     dte_effective BETWEEN :base_st_ymd  AND :base_end_ymd
            OR     dte_end       BETWEEN :base_st_ymd  AND :base_end_ymd);

          if (sqlca.sqlcode == 0)
          {
             if (tmp_count > 0)
             {
                memcpy(name_field, "SOBRA SAK CASE ", sizeof(name_field));
                memcpy(desc_field, "NO SPENDDOWN   ", sizeof(desc_field));
/*              memcpy(desc_field, base_id, 11); */
                cde_error = 2143;
                bValidSpendRec = FALSE;
             }
          }
          else
          {
             fprintf(stderr, "ERROR: could not retrieve records from t_re_elig for spenddown\n");
             fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
             fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
             return EXIT_FAILURE;
          }
        }
     }
     else
     {
        fprintf(stderr, "ERROR: could not retrieve SOBRA records from t_re_spend_liab for spenddown\n");
        fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }
   }
/*  .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   . SOBRA end   */
/*  CO14275 .   .   .   .   .   .   .   .   .   .   .   .   .   . INMAT start */
    if (sak_pub_hlth[0] == HLTH_INMAT && bValidSpendRec  == TRUE)
    {
      if (strncmp(case_number, ksc_case_number, sizeof(ksc_case_number)) !=  0)
      {
         if (verifyCaseNum() != 0)
         {
            fprintf (stderr, "ERROR: bad return from verifyCaseNum\n");
            return EXIT_FAILURE;
         }
      }
      sqlca.sqlcode = 0;
      EXEC SQL
         SELECT count(sak_spend_liab)
           INTO :tmp_count
           FROM t_re_spend_liab
          WHERE sak_case = :sak_case
            AND :benefit_month_yyyymmdd BETWEEN dte_effective AND dte_end;

      if (sqlca.sqlcode == 0)
      {
        if (tmp_count > 0)
        {
          if (base_st_ym == 0 && base_end_ym == 0)
          {
              memcpy(name_field, "INMAT SAK CASE ",  sizeof(name_field));
              memcpy(desc_field, "SPENDDOWN EXIST", sizeof(desc_field));
              cde_error = 2171;
              bValidSpendRec = FALSE;
          }
          else
              bValidSpenddown = TRUE;
        }
        if (base_st_ym > 0 && base_end_ym > 0 && bValidSpenddown == FALSE)
        {                     /* Checking HLTH_INMAT */
          sqlca.sqlcode = 0;
          EXEC SQL
            SELECT count(*)
              INTO :tmp_count
              FROM t_re_elig
             WHERE sak_recip    = :sak_recip
               AND sak_pub_hlth = 79
               AND cde_status1  = ' '
               AND (:base_st_ymd  BETWEEN dte_effective AND dte_end
                 or :base_end_ymd BETWEEN dte_effective AND dte_end
                 or dte_effective BETWEEN :base_st_ymd  AND :base_end_ymd
                 or dte_end       BETWEEN :base_st_ymd  AND :base_end_ymd);

          if (sqlca.sqlcode == 0)
          {
             if (tmp_count > 0)
             {
                memcpy(name_field, "INMAT SAK CASE ", sizeof(name_field));
                memcpy(desc_field, "NO SPENDDOWN   ", sizeof(desc_field));
/*              memcpy(desc_field, base_id, 11); */
                cde_error = 2170;
                bValidSpendRec = FALSE;
             }
          }
          else
          {
             fprintf(stderr, "ERROR: could not retrieve records from t_re_elig for spenddown\n");
             fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
             fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
             return EXIT_FAILURE;
          }
        }
     }
     else
     {
        fprintf(stderr, "ERROR: could not retrieve INMAT records from t_re_spend_liab for spenddown\n");
        fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }
   }
/*  .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   . INMAT end   */
   if (bValidSpendRec == FALSE)
   {
      if (insertErrTbl(cde_error) != 0)
      {
         fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
         return EXIT_FAILURE;
      }
      bEqualElig = TRUE;
   }

   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   checkProcessSpndwn()                               */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  6746     05/20/2004 Lisa Salgat      Initial Release                */
/************************************************************************/
static int checkProcessSpndwn()
{
int i=0;

   /*Since a bene can contain multiple segments, check to see if any of the segments pertain to spenddown*/
   while (sak_pub_hlth[i] != 0)
   {
      if ((sak_pub_hlth[i] == HLTH_MN    ||
           sak_pub_hlth[i] == HLTH_SOBRA ||
           sak_pub_hlth[i] == HLTH_INMAT)&&
           bValidSpendRec  == TRUE)
      {
         if ((base_st_ym > 0) && (base_end_ym > 0))
         {
            if (processSpndwn() != 0)
            {
               fprintf (stderr, "ERROR: bad return from processSpndwn\n");
               return EXIT_FAILURE;
            }
         }
      }
      ++i;
   }

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   processSpndwn()                                    */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/* CO11182   05/14/2008 Dale Gallaway    Modified the code to call mgd  */
/*                                       function when an eligibility   */
/*                                       record is errored off with an  */
/*                                       issue in spenddown base on the */
/*                                       incoming rec by adding a flag  */
/*                                       end_flag_set_from_processSpndwn*/
/************************************************************************/
static int processSpndwn()
{

int  bErrorSpenddown = FALSE;
int insert_dte_end;
int insert_dte_eff;
double insert_amt;
int update_dte_end;
int update_dte_eff;
double update_amt;
int update_sak;
int update_sak_case;
int cde_error = 0;
int flag = FALSE;

exec sql begin declare section;
int base_st_ymd;
int base_end_ymd;
exec sql end declare section;

    ksc_spend_amt = ksc_spend_amt/100;

    sqlca.sqlcode = 0;
    EXEC SQL
      SELECT to_char(last_day(to_date(:base_end_ym, 'YYYYMM')), 'YYYYMMDD'),
             :base_st_ym||'01'
      INTO   :base_end_ymd,
             :base_st_ymd
      FROM   dual;

    if (sqlca.sqlcode != 0)
    {
      fprintf(stderr, "ERROR: could not convert dates\n");
      fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
      return EXIT_FAILURE;
    }

    if (selectSpndwn() != 0)
    {
       fprintf(stderr, "ERROR: bad return from selectSpndwn\n");
       return EXIT_FAILURE;
    }

   if (sqlca.sqlcode == 100)
   {
      insert_dte_end = base_end_ymd;
      insert_dte_eff = base_st_ymd;
      insert_amt = ksc_spend_amt;

      if (insertSpndwn(insert_dte_eff, insert_dte_end, insert_amt) != 0)
      {
         fprintf(stderr, "ERROR: bad return from insertSpndwn\n");
         return EXIT_FAILURE;
      }
   }
   else
   {
     if (ksc_spend_amt != spn_amt_spenddown)
     {

       if (base_st_ym == spn_dte_effective_yyyymm)
       {

         if (base_end_ym == spn_dte_end_yyyymm)
         {

            if (ksc_spend_amt < spn_amt_spenddown)
            {
               if (claimsCheck(&flag) != 0)
               {
                  fprintf(stderr, "ERROR: bad return from claimsCheck\n");
                  return EXIT_FAILURE;
               }
               else
               {
                  if (flag == TRUE)
                  {
                     insert_dte_end = base_end_ymd;
                     insert_amt = ksc_spend_amt;

                     if (spndwnMet(base_st_ymd, base_end_ymd) != 0)
                     {
                        fprintf(stderr, "ERROR: bad return from spndwnMet\n");
                        return EXIT_FAILURE;
                     }
                  }
               }

               update_dte_end = spn_dte_end;
               update_dte_eff = spn_dte_effective;
               update_amt = ksc_spend_amt;
               update_sak = spn_sak_spend_liab;
               update_sak_case = spn_sak_case;

               if (updateSpendLiab(update_dte_eff, update_dte_end, update_amt, update_sak, update_sak_case) != 0)
               {
                  fprintf(stderr, "ERROR: bad return from updateSpndwn\n");
                  return EXIT_FAILURE;
               }
            }
            else if (ksc_spend_amt > spn_amt_spenddown)
            {
                 if (spn_sak_short > 0)
                 {
                   if (claimsCheck(&flag) != 0) /* check if new spenddown amount is met */
                   {
                     fprintf(stderr, "ERROR:  bad return from claimsCheck\n");
                     return EXIT_FAILURE;
                   }
                   else
                   {
                      if (flag == FALSE)  /* New Spenddown amount now unmet */
                      {
                        if (bMidMonthElig)
                        {
                           if (spndwnUnmet(benefit_month_min1day) != 0)
                           {
                              fprintf(stderr, "ERROR:  bad return from spndwnUnmet\n");
                              return EXIT_FAILURE;
                           }
                        }
                        else
                        {
                          if (neg_action_dte > parm_dte)
                          {
                             if (spn_dte_end <= curr_lastday)
                                bErrorSpenddown = TRUE;

                             if (spndwnUnmet(curr_lastday) != 0)
                             {
                                fprintf(stderr, "ERROR:  bad return from spndwnUnmet\n");
                                return EXIT_FAILURE;
                             }
                          }
                          else
                          {
                             if (spn_dte_end <= curr_dte_lstdy_pls1mth)
                                bErrorSpenddown = TRUE;

                             insert_dte_end = base_end_ym;
                             insert_dte_eff = base_st_ym;
                             insert_amt = ksc_spend_amt;

                             if (spndwnUnmet(curr_dte_lstdy_pls1mth) != 0)
                             {
                                fprintf(stderr, "ERROR: bad return from spndwnUnmet\n");
                                return EXIT_FAILURE;
                             }
                          }
                        }
                      }
                   }
                 }

               if (bErrorSpenddown)
               {
                  memcpy(name_field, "BASE END YM    ", sizeof(name_field));
                  memcpy(desc_field, "END DATES EQUAL", sizeof(desc_field));
                  cde_error = 2145;

                  end_process = TRUE;
                  end_flag_set_from_processSpndwn = TRUE;
                  if (insertErrTbl(cde_error) != 0)
                  {
                     fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
                     return EXIT_FAILURE;
                  }
               }
               else
               {
                  update_dte_end = spn_dte_end;
                  update_dte_eff = spn_dte_effective;
                  update_amt = ksc_spend_amt;
                  update_sak = spn_sak_spend_liab;
                  update_sak_case = spn_sak_case;

                  if (updateSpendLiab(update_dte_eff, update_dte_end, update_amt, update_sak, update_sak_case) != 0)
                  {
                     fprintf(stderr, "ERROR: bad return from updateSpndwn\n");
                     return EXIT_FAILURE;
                  }
               }
            }
         }
         else if (base_end_ym < spn_dte_end_yyyymm)
         {
            /* If a record exists on t_re_spend_met, updates dte_end to
               valid start or end date */
            if (spndwnValidate(base_st_ymd, base_end_ymd))
            {
               fprintf(stderr, "ERROR: bad return from spndwnValidate\n");
               return EXIT_FAILURE;
            }

            if (ksc_spend_amt < spn_amt_spenddown)
            {
               if (claimsCheck(&flag) != 0)
               {
                  fprintf(stderr, "ERROR: bad return from claimsCheck\n");
                  return EXIT_FAILURE;
               }
               else
               {
                  if (flag == TRUE)
                  {
                     if (spndwnMet(base_st_ymd, base_end_ymd) != 0)
                     {
                         fprintf(stderr, "ERROR: bad return from spndwnMet\n");
                         return EXIT_FAILURE;
                     }
                  }
               }
            }

            update_dte_end = base_end_ymd;
            update_dte_eff = base_st_ymd;
            update_amt = ksc_spend_amt;
            update_sak = spn_sak_spend_liab;
            update_sak_case = spn_sak_case;

            if (updateSpendLiab(update_dte_eff, update_dte_end, update_amt, update_sak, update_sak_case) != 0)
            {
               fprintf(stderr, "ERROR: bad return from updateSpndwn\n");
               return EXIT_FAILURE;
            }
         }
         else if (base_end_ym > spn_dte_end_yyyymm)
         {
            memcpy(name_field, "BASE END YM    ", sizeof(name_field));
            memcpy(desc_field, "ENDDTE > SPNDTE", sizeof(desc_field));
            cde_error = 2005;

            end_process = TRUE;
            end_flag_set_from_processSpndwn = TRUE;

            if (insertErrTbl(cde_error) != 0)
            {
               fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
               return EXIT_FAILURE;
            }
         }
      }
      else if (base_st_ym > spn_dte_effective_yyyymm)
      {
         memcpy(name_field, "BASE ST YM     ", sizeof(name_field));
         memcpy(desc_field, "EFFDTE > SPNEFF", sizeof(desc_field));
         cde_error = 2006;

         end_process = TRUE;
         end_flag_set_from_processSpndwn = TRUE;

         if (insertErrTbl(cde_error) != 0)
         {
            fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
            return EXIT_FAILURE;
         }

       }
     }
     else if (base_end_ym < spn_dte_end_yyyymm)
     {
        update_dte_end = base_end_ymd;
        update_dte_eff = spn_dte_effective;
        update_amt = spn_amt_spenddown;
        update_sak = spn_sak_spend_liab;
        update_sak_case = spn_sak_case;

        if (updateSpendLiab(update_dte_eff, update_dte_end, update_amt, update_sak, update_sak_case) != 0)
        {
           fprintf(stderr, "ERROR: bad return from updateSpndwn\n");
           return EXIT_FAILURE;
        }

        if (spndwnValidate(update_dte_eff, update_dte_end))
        {
           fprintf(stderr, "ERROR: bad return from spndwnValidate\n");
           return EXIT_FAILURE;
        }
     }

   }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   selectSpndwn()                                     */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int selectSpndwn()
{

      sqlca.sqlcode = 0;
      EXEC SQL
        SELECT a.sak_spend_liab,
               a.sak_case,
               a.dte_effective,
               to_number(to_char(to_date(a.dte_effective, 'YYYYMMDD'), 'YYYYMM')),
               a.dte_end,
               to_number(to_char(to_date(a.dte_end, 'YYYYMMDD'), 'YYYYMM')),
               a.amt_spenddown,
               NVL(b.sak_short, 0)
        INTO   :spn_sak_spend_liab,
               :spn_sak_case,
               :spn_dte_effective,
               :spn_dte_effective_yyyymm,
               :spn_dte_end,
               :spn_dte_end_yyyymm,
               :spn_amt_spenddown,
               :spn_sak_short
        FROM   t_re_spend_liab a,
               t_re_spend_met  b
        WHERE  a.sak_case         = :bene_sak_case
        AND    :base_st_ym||'01' between a.dte_effective and a.dte_end
        AND    a.sak_spend_liab   = b.sak_spend_liab(+)
        AND    NVL(b.sak_short,0) = ( SELECT NVL( MAX(sak_short), 0 )
                                        FROM t_re_spend_met m
                                       WHERE m.sak_spend_liab (+) = b.sak_spend_liab);

      if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
      {
         fprintf(stderr, "ERROR: could not select spenddown segment\n");
         fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   checkSpndwn()                                      */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int checkSpndwn(int *flag)
{

exec sql begin declare section;
int count = 0;
exec sql end declare section;

      sqlca.sqlcode = 0;
      EXEC SQL
        SELECT count (*)
        INTO   :count
        FROM   t_re_base b, t_re_spend_met s
        WHERE  b.id_medicaid = :id_medicaid
        AND    b.sak_case = s.sak_case
        AND    :benefit_month_yyyymmdd between s.dte_effective and s.dte_end;

      if ((sqlca.sqlcode != 0) && (sqlca.sqlcode == 100))
      {
         fprintf(stderr, "ERROR: could not check met spenddown\n");
         fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }
      else if (count > 0)
      {
         *flag = TRUE;
      }
      else
      {
         if ((base_st_ym > 0) && (base_end_ym > 0))
         {
            if (ksc_spend_amt == 0)
            {
               *flag = TRUE;
            }
            else
               *flag = FALSE;
         }
      }

      return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   claimsCheck()                                      */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int claimsCheck(int *flag)
{

exec sql begin declare section;
double total = 0;
exec sql end declare section;

   sqlca.sqlcode = 0;
   EXEC SQL
     SELECT NVL(sum(amt_pd_pat_ub92), 0)
     INTO   :total
     FROM   t_clm_patliab_x
     WHERE  sak_case      = :bene_sak_case
     AND    :base_st_ym  <= substr(dte_first_svc,0,6)
     AND    :base_end_ym >= substr(dte_last_svc,0,6)
     AND    cde_status1 = 'A'
     AND    ind_short_term in ('M', 'S');

     if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
     {
        fprintf(stderr,"ERROR: could not retrieve amt from patliab x table\n");
        fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }

    if (total >= ksc_spend_amt)
    {
       *flag = TRUE;
    }
    else
    {
       *flag = FALSE;
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   spndwnMet()                                        */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int spndwnMet(int eff, int end)
{

exec sql begin declare section;
int sak_short;
int dte_eff = 0;
int dte_end = 0;
exec sql end declare section;

   dte_eff = eff;
   dte_end = end;

   sqlca.sqlcode = 0;
   EXEC SQL
     SELECT nvl(max(sak_short),0)
     INTO   :sak_short
     FROM   t_re_spend_met
     WHERE  sak_spend_liab = :spn_sak_spend_liab;

     if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
     {
        fprintf(stderr,"ERROR: could not select max sak_short from spend met table\n");
        fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }

     if (sak_short == 0)
     {
        sak_short++;

        sqlca.sqlcode = 0;
        EXEC SQL
          INSERT INTO t_re_spend_met
                  (sak_spend_liab,
                   sak_short,
                   sak_case,
                   dte_effective,
                   dte_end,
                   dte_created,
                   dte_last_updated,
                   cde_create_source)
          VALUES   (:spn_sak_spend_liab,
                    :sak_short,
                    :sak_case,
                    :dte_eff,
                    :dte_end,
                    :curr_dte,
                    :curr_dte,
                    'K');

        if (sqlca.sqlcode != 0)
        {
           fprintf(stderr,"ERROR: could not insert into t_re_spend_met\n");
           fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
           fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
        }
     }
     else
     {
        sqlca.sqlcode = 0;
        EXEC SQL
           UPDATE t_re_spend_met
           SET    dte_effective     = :dte_eff,
                  dte_end           = :dte_end,
                  dte_last_updated  = :curr_dte
           WHERE  sak_spend_liab    = :spn_sak_spend_liab
           AND    sak_short         = :sak_short;

        if (sqlca.sqlcode != 0)
        {
           fprintf(stderr,"ERROR: could not update t_re_spend_met\n");
           fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
           fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
        }
      }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   spndwnValidate()                                   */
/*                                                                      */
/*  Description:    Validates dates on met table are within dates on    */
/*                  t_re_spend_liab table.                              */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*           11/24/2003 Lisa Salgat      Initial Release                */
/************************************************************************/
static int spndwnValidate(int start, int end)
{
exec sql begin declare section;
int end_date;
int eff_date;
int sak_short;
exec sql end declare section;

  int modify_flag = FALSE;

  sqlca.sqlcode = 0;
  EXEC SQL
    DECLARE SPNDCSR CURSOR FOR
     SELECT dte_effective,
            dte_end,
            sak_short
     FROM   t_re_spend_met
     WHERE  sak_case       = :bene_sak_case
     AND    sak_spend_liab = :spn_sak_spend_liab;

  EXEC SQL OPEN SPNDCSR;

  if (sqlca.sqlcode != 0)
  {
      fprintf(stderr, "ERROR: could not open SPNDCSR\n");
      fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
      return EXIT_FAILURE;
  }

  while (sqlca.sqlcode == 0)
  {
     EXEC SQL FETCH SPNDCSR
     INTO   :eff_date,
            :end_date,
            :sak_short;

     if (sqlca.sqlcode == 0)
     {
        if (eff_date > end)
        {
           eff_date    = start;
           end_date    = end;
           modify_flag = TRUE;
        }
        else if (end_date > end)
        {
           end_date    = end;
           modify_flag = TRUE;
        }

        if (modify_flag == TRUE)
        {
           sqlca.sqlcode = 0;
           EXEC SQL
              UPDATE t_re_spend_met
              SET    dte_effective     = :eff_date,
                     dte_end           = :end_date,
                     dte_last_updated  = :curr_dte
              WHERE  sak_spend_liab    = :spn_sak_spend_liab
              AND    sak_short         = :sak_short;

           if (sqlca.sqlcode != 0)
           {
              fprintf(stderr, "ERROR: could not update t_re_spend_met\n");
              fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
              fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
              return EXIT_FAILURE;
           }
        }
     }
     else if (sqlca.sqlcode != 100)
     {
         fprintf(stderr, "ERROR: could not select eff and end dates from spend met table\n");
         fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
     }
  }

  EXEC SQL CLOSE SPNDCSR;

  if (sqlca.sqlcode != 0)
  {
     fprintf(stderr, "ERROR: could not close SPNDCSR\n");
     fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
     fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
     return EXIT_FAILURE;
  }

  return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   spndwnUnmet()                                      */
/*                                                                      */
/*  Description:    Updates t_re_spend_met when spenddown increases.    */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*           08/29/2003 Lisa Salgat      Initial Release                */
/************************************************************************/
static int spndwnUnmet(int end)
{

exec sql begin declare section;
int sak_short;
int dte_end = 0;
exec sql end declare section;

   dte_end = end;

   if (dte_end < spn_dte_end)
   {
      sqlca.sqlcode = 0;
      EXEC SQL
        SELECT nvl(max(sak_short),0)
        INTO   :sak_short
        FROM   t_re_spend_met
        WHERE  sak_case       = :bene_sak_case
        AND    sak_spend_liab = :spn_sak_spend_liab
        AND    :dte_end between dte_effective and dte_end;

        if (sqlca.sqlcode != 0)
        {
           fprintf(stderr, "ERROR: could not select max sak_short from spend met table\n");
           fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
           fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
        }

        if (sak_short != 0)
        {
          sqlca.sqlcode = 0;
          EXEC SQL
            UPDATE t_re_spend_met
            SET    dte_end           = :dte_end,
                   dte_last_updated  = :curr_dte
            WHERE  sak_spend_liab    = :spn_sak_spend_liab
            AND    sak_short         = :sak_short
            AND    sak_case          = :sak_case;

          if (sqlca.sqlcode != 0)
          {
             fprintf(stderr, "ERROR: could not update t_re_spend_met\n");
             fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
             fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
             return EXIT_FAILURE;
          }
        }
     }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  function Name:   insertSpndwn()                                     */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int insertSpndwn(int eff, int end, double amt)
{

exec sql begin declare section;
int spn_eff;
int spn_end;
double spn_amt;
exec sql end declare section;

   spn_eff = eff;
   spn_end = end;
   spn_amt = amt;

   if (getNewSakSpendLiab() != 0)
   {
      fprintf (stderr, "ERROR: bad return from getNewSakSpendLiab\n");
      return EXIT_FAILURE;
   }

   sqlca.sqlcode = 0;
   EXEC SQL
     INSERT INTO t_re_spend_liab
          (sak_spend_liab,
           sak_case,
           dte_effective,
           dte_end,
           amt_spenddown,
           cde_time_period)
    VALUES (:new_sak_spend_liab,
            :bene_sak_case,
            :spn_eff,
            :spn_end,
            :spn_amt,
            'S');

        if (sqlca.sqlcode != 0)
        {
           fprintf(stderr,"ERROR: could not insert into t_re_spend_liab\n");
           fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
           fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
        }


        if (spn_amt == 0)
        {
           spn_sak_spend_liab = new_sak_spend_liab;

           if (spndwnMet(eff, end) != 0)
           {
              fprintf(stderr, "ERROR: bad return from spndwnMet\n");
              return EXIT_FAILURE;
           }
        }

       if (insertSpndHst(new_sak_spend_liab, spn_amt) != 0)
       {
          fprintf(stderr, "ERROR: bad return from insertSpndHst\n");
          return EXIT_FAILURE;
       }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateSpendLiab()                                  */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int updateSpendLiab(int eff, int end, double amt, int sak, int sak_case)
{
exec sql begin declare section;
int spn_eff;
int spn_end;
double spn_amt;
int spn_sak;
int spn_sak_case;
exec sql end declare section;

   spn_eff = eff;
   spn_end = end;
   spn_amt = amt;
   spn_sak = sak;
   spn_sak_case = sak_case;

   sqlca.sqlcode = 0;
   EXEC SQL
     UPDATE t_re_spend_liab
       SET dte_effective = :spn_eff,
           dte_end = :spn_end,
           amt_spenddown = :spn_amt,
           cde_time_period = 'S'
     WHERE sak_case = :spn_sak_case
     AND   sak_spend_liab = :spn_sak
     AND   (substr(:spn_eff,0,6)) between (substr(dte_effective,0,6)) and (substr(dte_end,0,6));

        if (sqlca.sqlcode != 0)
        {
           fprintf(stderr,"ERROR: could not update t_re_spend_liab\n");
           fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
           fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
        }

       if (insertSpndHst(spn_sak, spn_amt) != 0)
       {
          fprintf(stderr, "ERROR: bad return from insertSpndHst\n");
          return EXIT_FAILURE;
       }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertSpndHst()                                    */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int insertSpndHst(int sak, double amt)
{
exec sql begin declare section;
int spn_sak = 0;
int sak_short = 0;
int tmp_sak_short = 0;
double spn_amt = 0;
exec sql end declare section;

int bEqualSpenddown = FALSE;

   spn_sak = sak;
   spn_amt = amt;

   sqlca.sqlcode = 0;
   EXEC SQL
     SELECT nvl(max(sak_short),0)
     INTO   :sak_short
     FROM   t_re_spnd_liab_hst
     WHERE  sak_spend_liab = :spn_sak;

     if (sqlca.sqlcode != 0)
     {
        fprintf(stderr,"ERROR: could not select max sak_short from t_re_spend_liab_hst\n");
        fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }

   if (sak_short > 0)
   {
      sqlca.sqlcode = 0;
      EXEC SQL
        SELECT sak_short
        INTO   :tmp_sak_short
        FROM   t_re_spnd_liab_hst
        WHERE  sak_spend_liab = :spn_sak
        AND    amt_spenddown  = :spn_amt
        AND    sak_short      = :sak_short;

        if (sqlca.sqlcode != 0)
        {
           if (sqlca.sqlcode == 100)
           {
              bEqualSpenddown = FALSE;
           }
           else
           {
              fprintf(stderr, "ERROR: could not select sak_short from t_re_spend_liab_hst for amt_spenddown\n");
              fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
              fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
              return EXIT_FAILURE;
           }
        }
        else
        {
           bEqualSpenddown = TRUE;
        }
    }

    if ((sak_short == 0) || (!bEqualSpenddown))
    {
      sak_short++;

      sqlca.sqlcode = 0;
      EXEC SQL
        INSERT INTO t_re_spnd_liab_hst
                    (sak_spend_liab,
                     sak_short,
                     sak_case,
                     dte_effective,
                     amt_spenddown,
                     dte_created)
            VALUES  (:spn_sak,
                     :sak_short,
                     :sak_case,
                     :curr_dte,
                     :spn_amt,
                     :curr_dte);

        if (sqlca.sqlcode != 0)
        {
          fprintf(stderr, "ERROR: could not insert into t_re_spend_liab_hst\n");
          fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
        }
    }

   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateEpsdtLet()                                   */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int updateEpsdtLet()
{

    sqlca.sqlcode = 0;
    EXEC SQL
      INSERT INTO t_re_epsdt_letter
                 (sak_recip,
                  ind_letter,
                  dte_generic)
          VALUES (:new_sak_recip,
                  'N',
                  :curr_dte);

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not update t_re_epsdt_letter\n");
         fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   verifyCaseInfo()                                   */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO11849  04/05/2009 Srini Donepudi   Modified to capture the new    */
/*                                       fields case ssn, case dob      */
/************************************************************************/
static int verifyCaseInfo(char first[], char last[], char mid[])
{
exec sql begin declare section;
char nam_last[17+1];
char nam_first[12+1];
char nam_mid[1+1];
double amt21_premium;
double wh_premium;
exec sql end declare section;

    memcpy(nam_first, first, sizeof(nam_first));
    memcpy(nam_last, last, sizeof(nam_last));
    memcpy(nam_mid, mid, sizeof(nam_mid));

    amt21_premium = prem_hw_amt_t21/100;
    wh_premium    = prem_wh_amt/100;

    sqlca.sqlcode = 0;
    EXEC SQL
      UPDATE t_re_case
      SET    amt_hw_21_premium = :amt21_premium,
             amt_wh_premium = :wh_premium,
             nam_last = :nam_last,
             nam_first = :nam_first,
             nam_mid_init = :nam_mid,
             dte_birth = :case_dte_birth,
             num_ssn = :case_num_ssn
      WHERE  sak_case = :sak_case
      AND    num_case = :ksc_case_number
      AND   (amt_hw_21_premium <> :amt21_premium
      OR     amt_wh_premium <> :wh_premium
      OR     nam_last <> :nam_last
      OR     nam_first <> :nam_first
      OR     nam_mid_init <> :nam_mid
      OR     dte_birth <> :case_dte_birth
      OR     num_ssn <> :case_num_ssn);

      if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
      {
         fprintf(stderr, "ERROR: could not update t_re_case\n");
         fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   verifyCaseWorkerInfo()                             */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO14736  08/18/2013 Nirmal T         Initial Release                */
/************************************************************************/
static int verifyCaseWorkerInfo()
{

exec sql begin declare section;
    int  i_sak_short = 0;
    int open_date = OPEN_DATE;
    char s_case_worker[6+1] = {'\0'};
exec sql end declare section;

    EXEC SQL
    SELECT id_case_worker,
           sak_short_worker
    INTO   :s_case_worker,
           :i_sak_short
    FROM   t_re_case_worker_xref
    WHERE  sak_recip  = :sak_recip
    AND    cde_status = 'A';

    if (sqlca.sqlcode == 0)
    {
        if (strncmp(worker, s_case_worker, 6) != 0)
        {
            EXEC SQL
            UPDATE t_re_case_worker_xref
            SET    dte_end          = to_date(to_char(:curr_dte_min1day), 'YYYYMMDD'),
                   dte_last_updated = to_date(:char_curr_dte, 'YYYYMMDD'),
                   cde_status       = 'I'
            WHERE  sak_recip        = :sak_recip
            AND    sak_short_worker = :i_sak_short;

            if (sqlca.sqlcode != 0)
            {
                fprintf(stderr, "ERROR: could not update t_re_case_worker_xref\n");
                fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
                fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
                return EXIT_FAILURE;
            }

            i_sak_short++;
            if (insertCaseWorkerInfo(i_sak_short) != EXIT_SUCCESS)
            {
                fprintf (stderr, "ERROR: bad return from insertCaseWorkerInfo:[2]\n");
                return EXIT_FAILURE;
            }
        }
    }
    else if (sqlca.sqlcode == 100)
    {
        if (insertCaseWorkerInfo(1) != EXIT_SUCCESS)
        {
            fprintf (stderr, "ERROR: bad return from insertCaseWorkerInfo:[1]\n");
            return EXIT_FAILURE;
        }
    }
             
    else
    {
        fprintf(stderr, "ERROR: could not select t_re_case_worker_xref\n");
        fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
    }

      return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertCaseWorkerInfo()                             */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO14736  08/18/2013 Nirmal T         Initial Release                */
/************************************************************************/
static int insertCaseWorkerInfo(int lsak)
{

exec sql begin declare section;
    int  i_sak_short = lsak;
    int open_date = OPEN_DATE;
exec sql end declare section;

    EXEC SQL
    INSERT INTO t_re_case_worker_xref
        (sak_recip,
         sak_short_worker,
         id_case_worker,
         dte_effective,
         dte_end,
         dte_last_updated,
         cde_status )
        VALUES
        (:sak_recip,
         :i_sak_short,
         :worker,
         to_date(:char_curr_dte, 'YYYYMMDD'),
         to_date(to_char(:open_date), 'YYYYMMDD'),
         to_date(:char_curr_dte, 'YYYYMMDD'),
         'A');

    if (sqlca.sqlcode != 0)
    {
        fprintf(stderr, "ERROR: could not insert t_re_case_worker_xref\n");
        fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr, "ERROR: sqlca.sqlcode %d == %s\n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc);
        return EXIT_FAILURE;
    }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   processCase()                                      */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO12846  07/30/2010 Dale Gallaway    Change "&&" to "||" on the     */
/*                                       AS-FC logic for med_nam_first, */
/*                                       med_nam_last.                  */
/************************************************************************/
static int processCase()
{

exec sql begin declare section;
char lspaces[17+1] = "                 ";
char fspaces[12+1] = "            ";
char hold_first[12+1]=" ";
char hold_last[17+1]=" ";
char hold_mid[1+1]=" ";
exec sql end declare section;
int new_case_xref = FALSE;

    insert_case_dte_eff = 0;
    memset(insert_case_status, '\0', sizeof(insert_case_status));
    insert_case_dte_end = 0;

    memcpy(hold_first, case_nam_first, sizeof(case_nam_first));
    memcpy(hold_last,  case_nam_last,  sizeof(case_nam_last));
    memcpy(hold_mid,   case_nam_mid,   sizeof(case_nam_mid));

    if ((memcmp(cash_prog, "AS", sizeof(cash_prog))== 0) ||
        (memcmp(cash_prog, "FC", sizeof(cash_prog))== 0))
    {
       if ((memcmp(med_nam_first, fspaces, sizeof(med_nam_first)) != 0) || 
           (memcmp(med_nam_last, lspaces, sizeof(med_nam_last)) != 0))
       {
          memcpy(hold_first, med_nam_first, sizeof(med_nam_first));
          memcpy(hold_last,  med_nam_last,  sizeof(med_nam_last));
          memcpy(hold_mid,   med_nam_mid,   sizeof(med_nam_mid));
       }
    }

    case_dte_cert = orig_benefit_month;

    if (dte_cert > orig_benefit_month)
    {
        if (processCaseHist(&new_case_xref) != 0)
        {
           fprintf (stderr, "ERROR: bad return from processCaseHist\n");
           return EXIT_FAILURE;
        }
    }
    else
    {
       if (memcmp(case_number, ksc_case_number, sizeof(ksc_case_number)) != 0)
       {
         prem_case_change = 'y';
         strncpy (prem_case_num_prev, case_number, sizeof (prem_case_num_prev));

         if (bActiveElig == TRUE &&
             bActiveEligMonth == TRUE)
         {
            insert_case_dte_eff = orig_benefit_month;
            insert_case_status[0]  = case_cde_status[0];
            insert_case_dte_end = OPEN_DATE;

            if (dte_cert == orig_benefit_month)
            {
               /*if the bene month is the same as the start of the current */
               /* case xref, delete the current case xref */
               if (deleteCaseXref() != 0)
               {
                  fprintf (stderr, "ERROR: bad return from deleteCaseXref\n");
                  return EXIT_FAILURE;
               }
            }
            else
            {
               if (updateCaseXref(dte_cert, orig_benefit_month_min1day, HIST_CASE_XREF) != 0)
               {
                  fprintf (stderr, "ERROR: bad return from updateCaseXref\n");
                  return EXIT_FAILURE;
               }
            }
         }
         else
         {
            insert_case_dte_eff = orig_benefit_month;
            strncpy(insert_case_status, HIST_CASE_XREF, sizeof(insert_case_status));
            insert_case_dte_end = orig_benefit_month_lastday;

            if (dte_cert == orig_benefit_month)
            {
               if (updateCaseXref(orig_benefit_month_lastday_pls1, case_dte_end, case_cde_status) != 0)
               {
                  fprintf (stderr, "ERROR: bad return from updateCaseXref\n");
                  return EXIT_FAILURE;
               }
            }
            else /* dte_cert < benefit_month */
            {
               if (updateCaseXref(dte_cert, orig_benefit_month_min1day, HIST_CASE_XREF) != 0)
               {
                  fprintf (stderr, "ERROR: bad return from updateCaseXref\n");
                  return EXIT_FAILURE;
               }

               if (insertCaseXref(orig_benefit_month_lastday_pls1, case_dte_end, case_cde_status) != 0)
               {
                  fprintf (stderr, "ERROR: bad return from insertCaseXref\n");
                  return EXIT_FAILURE;
               }
            }
         }

         new_case_xref = TRUE;
       }
    }

    if (verifyCaseNum() != 0)
    {
       fprintf (stderr, "ERROR: bad return from verifyCaseNum\n");
       return EXIT_FAILURE;
    }

    if (sqlca.sqlcode == 100)
    {
       if (insertCase(hold_first, hold_last, hold_mid) != 0)
       {
          fprintf (stderr, "ERROR: bad return from insertCase\n");
          return EXIT_FAILURE;
       }

    }
    else if (sqlca.sqlcode == 0)
    {
       if (verifyCaseInfo(hold_first, hold_last, hold_mid) != 0)
       {
          fprintf (stderr, "ERROR: bad return from verifyCaseInfo\n");
          return EXIT_FAILURE;
       }
    }

      if (new_case_xref == TRUE)
      {
         bene_sak_case = sak_case;

        if (bActiveElig   != TRUE )
        {
         EXEC SQL
            UPDATE t_re_case_xref
               SET dte_end    = :insert_case_dte_end
             WHERE sak_case   = :bene_sak_case
               AND cde_status = 'H'
               AND dte_end    = to_number(to_char(to_date(:insert_case_dte_eff, 'YYYYMMDD')-1, 'YYYYMMDD'));

         if (sqlca.sqlcode == 100)
         {
            if (insertCaseXref(insert_case_dte_eff, insert_case_dte_end, insert_case_status) != 0)
            {
               fprintf (stderr, "ERROR: bad return from insertCaseXref\n");
               return EXIT_FAILURE;
            }
         }
         else if (sqlca.sqlcode != 0) 
         {
            fprintf (stderr, "ERROR: could not update case_xref for historical case\n");
            fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
            fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
            return EXIT_FAILURE;
         }
        }
        else
        {
            if (insertCaseXref(insert_case_dte_eff, insert_case_dte_end, insert_case_status) != 0)
            {
               fprintf (stderr, "ERROR: bad return from insertCaseXref\n");
               return EXIT_FAILURE;
            }
        }
      }
      else
      {
         if (verifyCaseXref() != 0)
         {
            fprintf (stderr, "ERROR: bad return from verifyCaseXref\n");
            return EXIT_FAILURE;
         }
      }

    if (new_case_xref == TRUE &&
        bActiveElig   == TRUE &&
        bActiveEligMonth == TRUE )
    {
       if (selectCaseInfo() != 0)
       {
          fprintf (stderr, "ERROR: bad return from selectCaseInfo\n");
          return EXIT_FAILURE;
       }

       sqlca.sqlcode = 0;
       EXEC SQL
       UPDATE t_re_base
       SET    sak_case = :sak_case
       WHERE  sak_case <> :sak_case
       AND    sak_recip = :sak_recip;

       if (sqlca.sqlcode != 0 && sqlca.sqlcode != 100)
       {
          fprintf (stderr, "ERROR: could not update sak_case on t_re_base \n");
          fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }
    }

    bene_sak_case = sak_case;
    bCaseVerified = TRUE;

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   processCaseHist()                                  */
/*                                                                      */
/*  Description:     Process case xref table when benefit month received*/
/*                   is less than current dte_cert.                     */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO8306   07/07/2005 Lisa Salgat      Initial Release                */
/************************************************************************/
static int processCaseHist(int *bInsCaseXref)
{
exec sql begin declare section;
int h_sak_case;
int h_dte_cert;
int min_dte_cert_min1;
int h_case_dte_end;
int h_case_xref;
char h_case_number[8+1];
char h_case_cde_status[1+1]=" ";
exec sql end declare section;

 int bUpdateXref = FALSE;

      memset(h_case_number, '\0', sizeof(h_case_number));

      insert_case_dte_eff = orig_benefit_month;
      strncpy(insert_case_status, HIST_CASE_XREF, sizeof(insert_case_status));

      EXEC SQL
        SELECT c.sak_case,
               c.num_case,
               x.dte_cert,
               x.cde_status,
               x.dte_end,
               x.sak_case_xref
        INTO  :h_sak_case,
              :h_case_number,
              :h_dte_cert,
              :h_case_cde_status,
              :h_case_dte_end,
              :h_case_xref
        FROM t_re_base b, t_re_case c, t_re_case_xref x
        WHERE b.sak_recip  = :sak_recip
        AND   b.sak_recip  = x.sak_recip
        AND   x.cde_status = 'H'
        AND   :orig_benefit_month BETWEEN dte_cert AND dte_end
        AND   x.sak_case   = c.sak_case
        AND   x.dte_last_updated = (SELECT MAX(dte_last_updated)
                                    FROM   t_re_case_xref
                                    WHERE  sak_recip = :sak_recip
                                    AND    :orig_benefit_month BETWEEN dte_cert and dte_end)
        AND   rownum = 1;
   
      if (sqlca.sqlcode == 0)
      {
         if (strncmp(h_case_number, ksc_case_number, sizeof(ksc_case_number)) != 0)
         {
            insert_case_dte_end = orig_benefit_month_lastday;
            *bInsCaseXref = TRUE;

           if (h_dte_cert == orig_benefit_month)
           {
               if (h_case_dte_end == orig_benefit_month_lastday)
               {
                  sqlca.sqlcode = 0;
                  EXEC SQL
                    DELETE t_re_case_xref
                    WHERE  sak_case_xref = :h_case_xref
                    AND    sak_recip     = :sak_recip;

                  if (sqlca.sqlcode != 0)
                  {
                     fprintf (stderr, "ERROR: could not update historical case xref \n");
                     fprintf (stderr, "ERROR: ID Medicaid %s, case_xref %d\n", id_medicaid, h_case_xref);
                     fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
                     return EXIT_FAILURE;
                  }
               }
               else
               {
                  bUpdateXref = TRUE;
                  h_dte_cert  = orig_benefit_month_lastday_pls1;
               }
           }
           else /* dte_cert < benefit_month */
           {
               bUpdateXref    = TRUE;

               if (orig_benefit_month_lastday < h_case_dte_end)
               {
                  if (insertCaseXref(orig_benefit_month_lastday_pls1, h_case_dte_end, "H") != 0)
                  {
                     fprintf (stderr, "ERROR: bad return from insertCaseXref\n");
                     return EXIT_FAILURE;
                  }
               }
               h_case_dte_end = orig_benefit_month_min1day;
           }

           if (bUpdateXref == TRUE)
           {
              EXEC SQL
                UPDATE t_re_case_xref
                SET    cde_status       = 'H',
                       dte_last_updated = :curr_dte,
                       dte_cert         = :h_dte_cert,
                       dte_end          = :h_case_dte_end
                WHERE  sak_case_xref    = :h_case_xref;

              if (sqlca.sqlcode != 0)
              {
                 fprintf (stderr, "ERROR: could not update historical case xref \n");
                 fprintf (stderr, "ERROR: ID Medicaid %s, case_xref %d\n", id_medicaid, h_case_xref);
                 fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
                 return EXIT_FAILURE;
              }
           }
         }
      }
      else if (sqlca.sqlcode == 100)
      {
         EXEC SQL
           SELECT to_number(to_char(to_date(MIN(x.dte_cert), 'YYYYMMDD')-1, 'YYYYMMDD'))
             INTO :min_dte_cert_min1
             FROM t_re_base b, t_re_case c, t_re_case_xref x
            WHERE b.sak_recip = :sak_recip
            AND   b.sak_recip = x.sak_recip
            AND   x.sak_case = c.sak_case;

         if (sqlca.sqlcode != 0 )
         {
            fprintf (stderr, "ERROR: could not select historical case xref \n");
            fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
            fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
            return EXIT_FAILURE;
         }

          insert_case_dte_end = min_dte_cert_min1;
          *bInsCaseXref     = TRUE;
      }
      else
      {
          fprintf (stderr, "ERROR: could not select historical case xref \n");
          fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }

      return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   selectCaseInfo()                                   */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO6811   06/24/2004 Lisa Salgat      Updated deceased select to not */
/*                                       include effective dates.       */
/************************************************************************/
static int selectCaseInfo()
{
   int cde_error = 0;

       sqlca.sqlcode = 0;
      /*try to get the Current record 1st*/
       EXEC SQL
         SELECT c.sak_case,
                c.num_case,
                x.dte_cert,
                x.cde_status,
                x.dte_end
         INTO  :sak_case,
               :case_number,
               :dte_cert,
               :case_cde_status,
               :case_dte_end
         FROM t_re_base b, t_re_case c, t_re_case_xref x
         WHERE b.sak_recip = :sak_recip
         AND   b.sak_recip = x.sak_recip
         AND   x.dte_end = 22991231
         AND   x.cde_status = 'C'
         AND   x.sak_case = c.sak_case;

       if (sqlca.sqlcode == 100)
       {
           /*if not, bene is deceased*/
           if (dte_death != 0 && benefit_month_yyyymmdd > dte_death)
           {
               /*bene is deceased and elig rec is for month after they became deceased */
               memcpy(name_field, "DTE DEATH      ", sizeof(name_field));
               memcpy(desc_field, "ELIG AFTER DOD ", sizeof(desc_field));
               cde_error = 2139;

               if (insertErrTbl(cde_error) != 0)
               {
                   fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
                   return EXIT_FAILURE;
               }

               end_process = TRUE;
               return EXIT_SUCCESS;
           }
           else
           {
               /*get deceased record*/
               sqlca.sqlcode = 0;
               EXEC SQL
                 SELECT c.sak_case,
                        c.num_case,
                        x.dte_cert,
                        x.cde_status,
                        x.dte_end
                 INTO  :sak_case,
                       :case_number,
                       :dte_cert,
                       :case_cde_status,
                       :case_dte_end
                 FROM t_re_base b, t_re_case c, t_re_case_xref x
                 WHERE b.sak_recip = :sak_recip
                 AND   b.sak_recip = x.sak_recip
                 AND   x.cde_status = 'D'
                 AND   x.sak_case = c.sak_case;
           }
       }

       if (sqlca.sqlcode != 0)
       {
           fprintf (stderr, "ERROR: could not select from base and case table\n");
           fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
           fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);

           memcpy(name_field, "ID MEDICAID    ", sizeof(name_field));
           memcpy(desc_field, "NOT ON B/CASE  ", sizeof(desc_field));
           cde_error = 2141;

           if (insertErrTbl(cde_error) != 0)
           {
               fprintf(stderr, "ERROR: bad return from insertErrTbl\n");
               return EXIT_FAILURE;
           }

           end_process = TRUE;
       }

       bene_sak_case = sak_case;

       return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   verifyCaseXref()                                   */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int verifyCaseXref()
{

   /* Only update for same case when active eligibility */

   if (bActiveElig == TRUE)
   {
       sqlca.sqlcode = 0;
       EXEC SQL
         UPDATE t_re_case_xref
         SET    cde_relationship = :relation_code,
                dte_cert         = :case_dte_cert,
	        	dte_last_updated = :curr_dte
         WHERE  sak_recip  = :sak_recip
         AND    sak_case = (select sak_case
                            from t_re_case
                            where num_case = :case_number)
         AND   cde_status = 'C'
         AND   (cde_relationship <> :relation_code
          OR    dte_cert          > :case_dte_cert   );

       if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
       {
          fprintf (stderr, "ERROR: could not update case xref table\n");
          fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }
   }

   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateCaseXref()                                   */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO14736  08/18/2013 Nirmal T         Added verifyCaseWorkerInfo call*/
/************************************************************************/
static int updateCaseXref(int lDteEff, int lDteEnd, char cStatus[])
{
exec sql begin declare section;
int lCaseDteEff = 0;
int lCaseDteEnd = 0;
char cCaseStatus[1+1];
exec sql end declare section;

       memset(cCaseStatus, '\0', sizeof(cCaseStatus));
       cCaseStatus[0] = cStatus[0];
       lCaseDteEff = lDteEff;
       lCaseDteEnd = lDteEnd;
       sqlca.sqlcode = 0;
       EXEC SQL
         UPDATE t_re_case_xref
         SET    cde_status       = :cCaseStatus,
	            dte_last_updated = :curr_dte,
                dte_cert         = :lCaseDteEff,
                dte_end          = :lCaseDteEnd
         WHERE  sak_case = (select sak_case
                            from t_re_case
                            where num_case = :case_number)
         AND    sak_recip= :sak_recip
         AND    cde_status <> 'H';

       if (sqlca.sqlcode != 0)
       {
          fprintf (stderr, "ERROR: could not update case_xref table\n");
          fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }

       if (verifyCaseWorkerInfo() != EXIT_SUCCESS)
       {
           fprintf (stderr, "ERROR: bad return from verifyCaseWorkerInfo[U]\n");
           return EXIT_FAILURE;
       }
    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   deleteCaseXref()                                   */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/26/2004 James Mercer     Initial Release                */
/************************************************************************/
static int deleteCaseXref()
{
       sqlca.sqlcode = 0;
       EXEC SQL
         DELETE t_re_case_xref
         WHERE  sak_case = (select sak_case
                            from t_re_case
                            where num_case = :case_number)
         AND    sak_recip= :sak_recip
         AND    cde_status = 'C';

       if (sqlca.sqlcode != 0)
       {
          fprintf (stderr, "ERROR: could not delete case_xref table\n");
          fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf(stderr, "ERROR: sak_case %d\n", sak_case);
          fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   verifyCaseNum()                                    */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int verifyCaseNum()
{

       sqlca.sqlcode = 0;
       EXEC SQL
         SELECT sak_case
         INTO  :sak_case
         FROM  t_re_case
         WHERE num_case = :ksc_case_number;

	if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
	{
           fprintf (stderr, "ERROR: could not select from sak case from case table\n");
           fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
           fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
        }

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   getCaseNum()                                       */
/*                                                                      */
/*  Description:     Get the sak_case for the case number.              */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11847  04/15/2009 Dale Gallaway    Premium Billing project        */
/************************************************************************/
static int getCaseNum (char gt_case_num [])
{
exec sql begin declare section;
int get_sak_case; 
char get_case_number [10+1]=" ";
exec sql end declare section;

    strncpy (get_case_number, gt_case_num, sizeof (get_case_number));
    get_sak_case = 0;

    sqlca.sqlcode = 0;
    EXEC SQL
    SELECT sak_case
      INTO :get_sak_case
      FROM t_re_case
     WHERE num_case = :get_case_number;

    if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
    {
       fprintf (stderr, "ERROR: could not select from sak case from case table. getCaseNum\n");
       fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
       fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
       return EXIT_FAILURE;
    }
    return get_sak_case;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertCase()                                       */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO11849  04/05/2009 Srini Donepudi   Modified to capture the new    */
/*                                       fields case ssn, case dob      */
/************************************************************************/
static int insertCase(char hold_first[], char hold_last[], char hold_mid[])
{

exec sql begin declare section;
char nam_first[12+1]=" ";
char nam_last[17+1]=" ";
char nam_mid[1+1]=" ";
double amt21_premium;
double wh_premium;
exec sql end declare section;

     amt21_premium  = prem_hw_amt_t21/100;
     wh_premium     = prem_wh_amt/100;

      if (getNewSakCase() != 0)
      {
         fprintf (stderr, "ERROR: bad return from getNewSakCase\n");
         return EXIT_FAILURE;
      }

      memcpy(nam_first, hold_first, sizeof(nam_first));
      memcpy(nam_last, hold_last, sizeof(nam_last));
      memcpy(nam_mid, hold_mid, sizeof(nam_mid));

      sqlca.sqlcode = 0;
      EXEC SQL
        INSERT INTO t_re_case
               (sak_case,
                num_case,
                amt_hw_21_premium,
                amt_wh_premium,
                nam_last,
                nam_first,
                nam_mid_init,
                dte_birth,
                num_ssn)
         VALUES (:new_sak_case,
                 :ksc_case_number,
                 :amt21_premium,
                 :wh_premium,
                 :nam_last,
                 :nam_first,
                 :nam_mid,
                 :case_dte_birth,
                 :case_num_ssn);

       if (sqlca.sqlcode != 0)
       {
          fprintf (stderr, "ERROR: could not insert into case table\n");
          fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
          return EXIT_FAILURE;
       }
    sak_case      = new_sak_case;
    bene_sak_case = new_sak_case;

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertCaseXref()                                   */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*  CO14736  08/18/2013 Nirmal T         Added verifyCaseWorkerInfo call*/
/************************************************************************/
static int insertCaseXref(int lDteEff, int lDteEnd, char cStatusCde[])
{

exec sql begin declare section;
int lCaseEndDate = lDteEnd;
int lCaseEffDate = lDteEff;
char cCaseStatCde[1+1];
exec sql end declare section;

      if (getNewSakCaseXref() != 0)
      {
         fprintf (stderr, "ERROR: bad return from getNewSakCaseXref\n");
         return EXIT_FAILURE;
      }

      memset(cCaseStatCde, '\0', sizeof(cCaseStatCde));
      cCaseStatCde[0] = cStatusCde[0];

      sqlca.sqlcode = 0;
      EXEC SQL
        INSERT INTO t_re_case_xref
               (sak_case_xref,
                sak_case,
                sak_recip,
                dte_cert,
                dte_end,
                cde_status,
                cde_relationship,
                dte_last_updated)
         VALUES (:new_sak_case_xref,
                 :bene_sak_case,
                 :sak_recip,
                 :lCaseEffDate,
                 :lCaseEndDate,
                 :cCaseStatCde,
                 :relation_code,
                 :curr_dte);

       if (sqlca.sqlcode != 0)
       {
          fprintf (stderr, "ERROR: could not insert into case xref table\n");
          fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }

       if (verifyCaseWorkerInfo() != EXIT_SUCCESS)
       {
           fprintf (stderr, "ERROR: bad return from verifyCaseWorkerInfo[I]\n");
           return EXIT_FAILURE;
       }
    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   processPremBill()                                  */
/*                                                                      */
/*  Description:     Check to see if the elig is HealthWave (HW) or     */
/*                   Working Healthy (WH).                              */
/*                   If bene is premium_billing                         */
/*                     call updatePrem ()                               */
/*                   else                                               */
/*                     If bene WAS premium_billing                      */
/*                       call lookForPremBill ()                        */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11847  04/15/2009 Dale Gallaway    Premium Billing project        */
/************************************************************************/
static int processPremBill()
{
char   do_prem_bill = 'y';
int    rc_check = 0;
double preem_amt    = 0;
double sv_amt_premm = 99999; 
EXEC SQL BEGIN DECLARE SECTION;
int premlook_sak_case;
EXEC SQL END DECLARE SECTION;

   prem_eff_date = benefit_month_yyyymmdd; 
   prem_end_date = benefit_month_lastday;
   if (prem_end_date > 22990000) 
       prem_end_date = orig_benefit_month_lastday;

   if ((sak_pub_hlth [0] == 4 && retro_t21[0] != 'Y') 
   &&  (benefit_month_yyyymm == curr_dte_yyyymm)
   &&  (bAddedRecord == FALSE) && (updat_aid_flag == FALSE))
   {
      sv_amt_premm = premBillExist (sak_case, "HW");  
      if (sv_amt_premm == 0) 
      {
         prem_eff_date = benefit_month_lastday_pls1;
         prem_end_date = curr_dte_lstdy_pls1mth; 
         return EXIT_SUCCESS; 
      }
   }

/*....Dec 21 2009 .....................................start...*/ 
   if ((sak_pub_hlth [0] == 4 && retro_t21[0] != 'Y') 
   &&  (benefit_month_yyyymm == curr_dte_yyyymm))
   {
      rc_check = premBillCovT21 (sak_recip);  
      if (rc_check == 0) 
      {
         prem_eff_date = benefit_month_lastday_pls1;
         prem_end_date = curr_dte_lstdy_pls1mth; 
         return EXIT_SUCCESS; 
      }
   }
/*....Dec 21 2009 .....................................end.....*/ 

   if (checkPremBill() != 0 )
   {
      fprintf (stderr, "ERROR: Problem with checkPremBill(). \n");
      return EXIT_FAILURE;
   }

   if ((is_hw == 'y') || (is_wh == 'y'))
   {
      prem_sak_case = sak_case;
      if (is_wh == 'y')
      {
         preem_amt = prem_wh_amt;
         preem_amt = preem_amt/100;
         memcpy(cde_prem_type, "WH", sizeof(cde_prem_type));
      }
      else 
      {
         preem_amt = prem_hw_amt_t21;
         preem_amt = preem_amt/100;
         memcpy(cde_prem_type, "HW", sizeof(cde_prem_type));

         if (sv_amt_premm == 99999) 
            sv_amt_premm = premBillExist (sak_case, "HW");  

         /* Found a retro T21 record. Since the amount is the same, we are done here. */ 
         if (sv_amt_premm == preem_amt) 
         {
            prem_eff_date = benefit_month_lastday_pls1;
            prem_end_date = curr_dte_lstdy_pls1mth; 
            return EXIT_SUCCESS; 
         }
      }
  
      if ((bAddedRecord == FALSE) && (updat_aid_flag == FALSE) && (prem_case_change == 'y'))
      {  
         do_prem_bill = 'n'; 
      }

      if (do_prem_bill == 'y') 
      {
         if ((benefit_month_yyyymm <= prem_bill_yyyymm) && (runParm == DAILY))
         {
            if (premBillErrRpt() != 0 )                   /* DATA for ERROR REPORT. */ 
            {
               fprintf (stderr, "ERROR: Problem with premBillErrRpt(). \n");
               return EXIT_FAILURE;
            }
            return EXIT_SUCCESS;
         }
         if (insertPreBillBene (sak_case) != 0 )
         {
            fprintf (stderr, "ERROR: Problem with insertPreBillBene(). \n");
            return EXIT_FAILURE;
         }
         if (updatePrem (sak_case, preem_amt) != 0)
         {
            fprintf (stderr, "ERROR: bad return from updatePrem(). \n");
            return EXIT_FAILURE;
         }
         if (prem_case_change == 'y') 
         {
            premlook_sak_case = getCaseNum (prem_case_num_prev); 

            if (lookForPremBill (premlook_sak_case) != 0 )
            {
               fprintf (stderr, "ERROR: Problem with lookForPremBill (premlook_sak_case). \n");
               return EXIT_FAILURE;
            }
         }
      }
   }
   else if ((was_hw == 'y') || (was_wh == 'y'))
   {
      prem_sak_case = sak_case;
      if (was_wh == 'y')
         memcpy (cde_prem_type, "WH", sizeof(cde_prem_type));
      if (was_hw == 'y')
         memcpy (cde_prem_type, "HW", sizeof(cde_prem_type));

      if (insertPreBillBene (sak_case) != 0 )
      {
         fprintf (stderr, "ERROR: Problem with insertPreBillBene(). \n");
         return EXIT_FAILURE;
      }

      if (lookForPremBill (sak_case) != 0 )
      {
         fprintf (stderr, "ERROR: Problem with lookForPremBill (sak_case). \n");
         return EXIT_FAILURE;
      }
   }
   return (0);
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   checkPremBill()                                    */
/*                                                                      */
/*  Description:     Check to see if the elig is HealthWave (HW) or     */
/*                   Working Healthy (WH).                              */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11847  04/15/2009 Dale Gallaway    Premium Billing project        */
/************************************************************************/
static int checkPremBill()
{
   is_hw = 'n';
   is_wh = 'n';

   if (sak_pub_hlth[0] == 4)
   { 
      is_hw = 'y';
/* CO11847 HealthWave shut off. */ 
/*    is_hw = 'n';              */
   }

   if ((memcmp (prog_type, "MS", 2) == 0))
   {
      if ((memcmp (med_elig_ind, "WH", 2) == 0)
      ||  (memcmp (med_elig_ind, "WL", 2) == 0)
      ||  (memcmp (med_elig_ind, "WM", 2) == 0)
      ||  (memcmp (med_elig_ind, "WQ", 2) == 0)
      ||  (memcmp (pres_dis_ind, "WH", 2) == 0))
      {
         is_wh = 'y';
      }
   }
   return (0);
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   premBillExist()                                    */
/*                                                                      */
/*  Description:     Check to see if there is a record on t_re_premium  */
/*                   for the time period given. Return the amount.      */
/*                                                                      */
/************************************************************************/
static double premBillExist (int ucase1, char premm_type1 [3]) 
{
exec sql begin declare section;
double sql_amt_premm = 0;
char   premm_type2 [3];
int   ucase2; 
exec sql end declare section;

   ucase2 = ucase1; 
   strcpy (premm_type2, premm_type1);  

   EXEC SQL
   SELECT amt_premium 
     INTO :sql_amt_premm
     FROM t_re_premium
    WHERE sak_case         = :ucase2
      AND dte_end          > :prem_eff_date
      AND dte_effective    < :prem_end_date
      AND cde_premium_type = :premm_type2  
      AND cde_status      <> 'H'; 

   if (sqlca.sqlcode != 0) 
   {
      return (0);
   }
   return sql_amt_premm;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   premBillCovT21 ()                                  */
/*                                                                      */
/*  Description:     Check to see if there is a full months coverage    */
/*                   for Title 21.  return: 0 is no,  1 is yes.         */
/*                                                                      */
/************************************************************************/
static int premBillCovT21 (int urecp1) 
{
exec sql begin declare section;
int  rc_return = 0;
int urecp2; 
exec sql end declare section;

   urecp2 = urecp1; 

   EXEC SQL
   SELECT 1  
     INTO :rc_return 
     FROM t_re_elig 
    WHERE sak_recip      = :urecp2
      AND dte_effective <= :prem_eff_date
      AND dte_end       >= :prem_end_date
      AND sak_pub_hlth   = 4   
      AND cde_status1   <> 'H'; 

   if (sqlca.sqlcode != 0) 
   {
      return (0); 
   }
   return rc_return;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updatePrem()                                       */
/*                                                                      */
/*  Description:   Make the updates to the Prem Bill table.             */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11847  04/15/2009 Dale Gallaway    Premium Billing project        */
/************************************************************************/
static int updatePrem (int ucase1, double u_amt1)
{
exec sql begin declare section;
int prem_count = 0;
int ucase2; 
double u_amt2;
exec sql end declare section;

   ucase2 = ucase1;
   u_amt2 = u_amt1; 

   EXEC SQL
   SELECT count(*)
     INTO :prem_count
     FROM t_re_premium
    WHERE sak_case         = :ucase2
      AND dte_end          > :prem_eff_date
      AND dte_effective    < :benefit_month_lastday
      AND cde_premium_type = :cde_prem_type 
      AND cde_status      <> 'H'; 

   if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
   {
      fprintf (stderr, "ERROR: could not select count from premium table.[1]\n");
      fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
      return EXIT_FAILURE;
   }
   if (prem_count == 0)
   {
      if (isrtPremLogic (ucase1, u_amt1) != 0)
      {
         fprintf (stderr, "ERROR: bad return from isrtPremLogic\n");
         return EXIT_FAILURE;
      }
      return EXIT_SUCCESS;
   }
   /* A premium record was found. */ 

   sv_sak_premium3 = 0; 

   EXEC SQL
   SELECT sak_premium
        , dte_effective 
        , dte_end 
        , amt_premium
     INTO :sv_sak_premium3
        , :sv_prem_eff3
        , :sv_prem_end3
        , :sv_prem_amt3 
     FROM t_re_premium
    WHERE sak_case         = :ucase2
      AND dte_end          > :prem_eff_date
      AND dte_effective    < :prem_end_date
      AND cde_premium_type = :cde_prem_type 
      AND cde_status      <> 'H'
      AND amt_premium     <> :u_amt2; 

   if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
   {
      fprintf (stderr, "ERROR: could not select count from premium table.[2]\n");
      fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
      return EXIT_FAILURE;
   }
   if (sv_sak_premium3 == 0)  
   {
      return (0);
   }
   /* A premium record was found with a different amount. Need to set its status to 'H'. */ 

   if (histPremLogic (ucase2, sv_prem_eff3, sv_prem_end3, sv_prem_amt3, u_amt2, sv_sak_premium3) != 0 )
   {
      fprintf (stderr, "ERROR: Problem with histPremLogic(). \n");
      return EXIT_FAILURE;
   }
   if (u_amt2 > 0)
   {
      if (insertPrem (ucase2, prem_eff_date, prem_end_date, u_amt2, ' ') != 0 )
      {
         fprintf (stderr, "ERROR: Problem with insertPrem(). \n");
         return EXIT_FAILURE;
      }
   }
   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name: histPremLogic()                                      */
/*                                                                      */
/*  Description: History off a premium record.                          */
/*               First, history off the record found.                   */
/*               If the record found extends before the benefit month,  */
/*                 add the old amount back out there.                   */
/*               If the record found extends beyond the benefit month,  */
/*                 add the old amount back out there.                   */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11847  04/15/2009 Dale Gallaway    Premium Billing project        */
/************************************************************************/
static int histPremLogic (int hcase1, int hprem_eff, int hprem_end, double hprem_amt_old, double hprem_amt_new, int hprem_sak1)
{
exec sql begin declare section;
int hprem_sak2;
exec sql end declare section;
   hprem_sak2 = hprem_sak1; 

   EXEC SQL
   UPDATE t_re_premium
      SET cde_status       = 'H' 
        , dte_last_updated = :curr_dte
        , dte_active_thru  = :curr_dte
    WHERE sak_premium      = :hprem_sak2;

   if (sqlca.sqlcode != 0) 
   {
      fprintf (stderr, "ERROR: could not update t_re_premium table. histPremLogic(1) \n");
      fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
      return EXIT_FAILURE;
   }

   if (hprem_eff < prem_eff_date) 
   {
      if (insertPrem (hcase1, hprem_eff, benefit_month_min1day, hprem_amt_old, ' ') != 0 )
      {
         fprintf (stderr, "ERROR: Problem with insertPrem(). histPremLogic(1) \n");
         return EXIT_FAILURE;
      }
   }

   if (hprem_amt_new == 0) 
   {
      if (insertPrem (hcase1, prem_eff_date, hprem_end, 0, ' ') != 0 )
      {
         fprintf (stderr, "ERROR: Problem with insertPrem(). Amount = 0.  \n");
         return EXIT_FAILURE;
      }
   }
   else 
   {
      if (hprem_end > prem_end_date) 
      {
         if (insertPrem (hcase1, benefit_month_lastday_pls1, hprem_end, hprem_amt_old, ' ') != 0 )
         {
            fprintf (stderr, "ERROR: Problem with insertPrem(). histPremLogic(2) \n");
            return EXIT_FAILURE;
         }
      }
   }
   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   isrtPremLogic()                                    */
/*                                                                      */
/*  Description:     A premium amount needs to be inserted.             */
/*                   Does a record exist with the same amount right next*/
/*                   to the month we want to insert?                    */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11847  04/15/2009 Dale Gallaway    Premium Billing project        */
/************************************************************************/
static int isrtPremLogic (int isrt_case1, double isrt_amt1)
{
exec sql begin declare section;
int isrt_case2;
double isrt_amt2;
exec sql end declare section;
   isrt_case2      = isrt_case1;
   isrt_amt2       = isrt_amt1;
   sv_sak_premium1 = 0;
   sv_sak_premium2 = 0;
                                            /* Is the prior month out there? */ 
   EXEC SQL
   SELECT sak_premium 
     INTO :sv_sak_premium1 
     FROM t_re_premium
    WHERE sak_case      = :isrt_case2
      AND dte_end       = :benefit_month_min1day
      AND cde_status   <> 'H'
      AND cde_premium_type = :cde_prem_type
      AND amt_premium      = :isrt_amt2; 

   if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
   {
      fprintf (stderr, "ERROR: could not select count from premium table. isrtPremLogic #1 \n");
      fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
      return EXIT_FAILURE;
   }
                                            /* Is the next month out there? */ 
   EXEC SQL
   SELECT sak_premium 
        , dte_end 
     INTO :sv_sak_premium2 
        , :sv_prem_end5 
     FROM t_re_premium
    WHERE sak_case         = :isrt_case2
      AND dte_effective    = :benefit_month_lastday_pls1
      AND cde_status      <> 'H'
      AND cde_premium_type = :cde_prem_type
      AND amt_premium      = :isrt_amt2; 

   if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
   {
      fprintf (stderr, "ERROR: could not select count from premium table. isrtPremLogic #2 \n");
      fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
      return EXIT_FAILURE;
   }

   if ((sv_sak_premium1 > 0) && (sv_sak_premium2 > 0))   
   {
      if (slotPremBill() != 0 )
      {
         fprintf (stderr, "ERROR: Problem with slotPremBill(). \n");
         return EXIT_FAILURE;
      }
      return EXIT_SUCCESS;
   }
   if (sv_sak_premium1 > 0)  
   {
      EXEC SQL
      UPDATE t_re_premium
         SET dte_end          = :benefit_month_lastday
           , dte_last_updated = :curr_dte
       WHERE sak_premium = :sv_sak_premium1; 

      if (sqlca.sqlcode == 0)
         return EXIT_SUCCESS;
      else 
      {
         fprintf (stderr, "ERROR: could not update t_re_premium table. isrtPremLogic 1 \n");
         fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
         return EXIT_FAILURE;
      }
   }   
   if (sv_sak_premium2 > 0)  
   {
      EXEC SQL
      UPDATE t_re_premium
         SET dte_effective    = :prem_eff_date 
           , dte_last_updated = :curr_dte
       WHERE sak_premium   = :sv_sak_premium2; 

      if (sqlca.sqlcode == 0)
         return EXIT_SUCCESS;
      else 
      {
         fprintf (stderr, "ERROR: could not update t_re_premium table. isrtPremLogic 2 \n");
         fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
         return EXIT_FAILURE;
      }
   }   
   if (insertPrem (isrt_case2, prem_eff_date, prem_end_date, isrt_amt2, ' ') != 0 )
   {
      fprintf (stderr, "ERROR: Problem with insertPrem(). \n");
      return EXIT_FAILURE;
   }
   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   slotPremBill()                                     */
/*                                                                      */
/*  Description:  We need to "slot in" a premium billing record between */
/*                2 records, do this by                                 */
/*                  1). History off the month-after record.             */
/*                  2). Extend the end date of the month-before record. */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11847  04/15/2009 Dale Gallaway    Premium Billing project        */
/************************************************************************/
static int slotPremBill()
{
   EXEC SQL
   UPDATE t_re_premium
      SET cde_status       = 'H'   
        , dte_active_thru  = :curr_dte
        , dte_last_updated = :curr_dte
    WHERE sak_premium      = :sv_sak_premium2; 

   if (sqlca.sqlcode != 0)
   {
      fprintf (stderr, "ERROR: could not update t_re_premium table. slotPremBill 1 \n");
      fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
      return EXIT_FAILURE;
   }
   EXEC SQL
   UPDATE t_re_premium
      SET dte_end          = :sv_prem_end5  
        , dte_last_updated = :curr_dte
    WHERE sak_premium      = :sv_sak_premium1; 

   if (sqlca.sqlcode != 0)
   {
      fprintf (stderr, "ERROR: could not update t_re_premium table. slotPremBill 2 \n");
      fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
      return EXIT_FAILURE;
   }
   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   premBillErrRpt()                                   */
/*                                                                      */
/*  Description:     Create a record for the ELG-0010-D Premium Billing */
/*                   Error Report.                                      */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11847  04/29/2009  Dale Gallaway   Initial Release                */
/************************************************************************/
static int premBillErrRpt ()
{
   memset (prem_bill_rec, ' ', sizeof (PREM_BILL_ERR_EXT));  
   memcpy (prem_bill_rec->num_case   ,  ksc_case_number, sizeof (prem_bill_rec->num_case));   
   prem_bill_rec->num_case [8] = ' ';
   prem_bill_rec->num_case [9] = ' ';
   sprintf (sv_sk_recip, "%09d", sak_recip); 
   memcpy (prem_bill_rec->sak_recip  ,  sv_sk_recip    , sizeof (prem_bill_rec->sak_recip));   
   memcpy (prem_bill_rec->id_medicaid,  id_medicaid, sizeof (prem_bill_rec->id_medicaid));   

   sprintf (sv_ben_mon, "%6d", benefit_month_yyyymm); 
   memcpy (prem_bill_rec->bene_month ,  sv_ben_mon , sizeof (prem_bill_rec->bene_month ));   

   if (is_hw == 'y')
   {
      memcpy (prem_bill_rec->prem_type, "HW" , sizeof (prem_bill_rec->prem_type));   
      sprintf (char5, "%05d", prem_hw_amt_t21); 
      memcpy (prem_bill_rec->prem_amt , char5, sizeof (prem_bill_rec->prem_amt ));   
   }
   else
   {
      memcpy (prem_bill_rec->prem_type, "WH" , sizeof (prem_bill_rec->prem_type));   
      sprintf (char5, "%05d", prem_wh_amt); 
      memcpy (prem_bill_rec->prem_amt , char5, sizeof (prem_bill_rec->prem_amt ));   
   }

   /* fprintf (prembillout, "%s\n", prem_bill_rec );    */
   fwrite(prem_bill_rec, sizeof(PREM_BILL_ERR_EXT), 1, prembillout);
   fwrite("\n", sizeof(char), 1, prembillout);

   prem_bill_ctr++; 
   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name: lookForPremBill ()                                   */
/*                                                                      */
/*  Description:   Look for a premium beneficiary.                      */
/*                 If none found, create a zero record.                 */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11847  04/29/2009  Dale Gallaway   Initial Release                */
/************************************************************************/
static int lookForPremBill (int prem_look_sak_case) 
{
exec sql begin declare section;
int hw_count = 0;
int wh_count = 0;
int premlook_sak_case = 0;
exec sql end   declare section;

   premlook_sak_case = prem_look_sak_case;

   EXEC SQL
   select count(*) 
     into :hw_count
     from t_re_elig      e,
          t_re_case_xref x
    where x.sak_case      = :premlook_sak_case
      and x.sak_recip     = e.sak_recip 
      and :prem_eff_date  between  x.dte_cert      and x.dte_end
      and :prem_eff_date  between  e.dte_effective and e.dte_end
      and e.sak_pub_hlth  = 4
      and e.cde_status1  <> 'H'; 

   if (sqlca.sqlcode != 0)
   {
      fprintf (stderr, "ERROR: could not select count from lookForPremBill1. \n");
      fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
      return EXIT_FAILURE;
   }

   exec sql
   select count(*) 
     into :wh_count
     from t_re_aid_elig  a,
          t_re_case_xref x,
          t_cde_aid      c 
    where x.sak_case      = :premlook_sak_case
      and x.sak_recip     = a.sak_recip 
      and :prem_eff_date  between  x.dte_cert      and x.dte_end
      and :prem_eff_date  between  a.dte_effective and a.dte_end
      and a.kes_cde_med_type = 'MS'
      and (a.kes_cde_med_elig in ('WH', 'WL', 'WM', 'WQ') 
          or       
           a.kes_cde_pdi = 'WH')
      and a.cde_status1  <> 'H'
      and a.sak_cde_aid  = c.sak_cde_aid 
      and c.cde_aid_category in ('26', '27'); 

   if (sqlca.sqlcode != 0)
   {
      fprintf (stderr, "ERROR: could not select count from lookForPremBill2. \n");
      fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
      return EXIT_FAILURE;
   }

   if (((prem_case_change == 'y') && (is_wh == 'y') && (wh_count == 0))
         || 
       ((prem_case_change == 'y') && (is_hw == 'y') && (hw_count == 0))
         || 
       ((was_wh == 'y') && (wh_count == 0))
         || 
       ((was_hw == 'y') && (hw_count == 0))) 
   {
      if (updatePrem (premlook_sak_case, 0) != 0 )
      {
         fprintf (stderr, "ERROR: Problem with updatePrem(). lookfor\n");
         return EXIT_FAILURE;
      }
      if (prem_case_change == 'y')
      {
         if (insertPreBillBene (premlook_sak_case) != 0 )
         {
            fprintf (stderr, "ERROR: Problem with insertPreBillBene(). \n");
            return EXIT_FAILURE;
         }
      }
   }
   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertPrem()                                       */
/*                                                                      */
/*  Description:  Insert to t_re_premium.                               */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11847  04/15/2009 Dale Gallaway    Premium Billing project        */
/************************************************************************/
static int insertPrem (int iprem_case1, int iprem_eff1, int iprem_end1, double iprem_amt1, char iprem_status1)
{
exec sql begin declare section;
int iprem_case2;
int iprem_eff2;
int iprem_end2;
double iprem_amt2;
char iprem_status2 [2];
int prem_date_active_thru; 
exec sql end declare section;

   iprem_case2   = iprem_case1;
   iprem_eff2    = iprem_eff1;
   iprem_end2    = iprem_end1;
   iprem_amt2    = iprem_amt1;
   iprem_status2 [0] = iprem_status1;
   iprem_status2 [1] = '\0';

   if (getNewSakPremium() != 0)
   {
      fprintf (stderr, "ERROR: bad return from getNewSakPremium \n");
      return EXIT_FAILURE;
   }

   sqlca.sqlcode         = 0;
   prem_date_active_thru = open_date; 

   if (iprem_status2 [0] == 'H')
   {
      prem_date_active_thru = curr_dte; 
   }

   EXEC SQL
   INSERT INTO t_re_premium
              (sak_premium, 
               sak_case,
               dte_effective,
               dte_end,
               cde_premium_type,
               amt_premium,
               cde_status,
               dte_created,
               dte_last_updated,
               dte_active_thru)
   VALUES (:new_sak_premium,
               :iprem_case2,
               :iprem_eff2, 
               :iprem_end2, 
               :cde_prem_type,
               :iprem_amt2,
               :iprem_status2, 
               :curr_dte,
               :curr_dte,
               :prem_date_active_thru); 

   if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != -1))
   {
      fprintf (stderr, "ERROR: could not insert into t_re_premium table\n");
      fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
      fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
      return EXIT_FAILURE;
   }
   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertPreBillBene()                                */
/*                                                                      */
/*  Description:  Insert to t_re_kaecses_recips                         */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11847  04/15/2009 Dale Gallaway    Premium Billing project        */
/************************************************************************/
static int insertPreBillBene (int iprem_case1)
{
exec sql begin declare section;
int iprem_case2; 
exec sql end declare section;
   if (runParm == MONTHLY) 
      return EXIT_SUCCESS;

   iprem_case2   = iprem_case1;
   sqlca.sqlcode = 0;   
   EXEC SQL
   INSERT INTO t_re_kaecses_recips
              (sak_recip, 
               sak_case)
   VALUES (:sak_recip,
           :iprem_case2); 

   if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != -1)) 
   {
      fprintf (stderr, "ERROR: could not insert into t_re_kaecses_recips.\n");
      fprintf (stderr, "ERROR: sak_recip: %d  sak_case: %d  ID Medicaid %s\n", sak_recip, prem_sak_case, id_medicaid);
      fprintf (stderr, "sqlcode: %d   %s \n", sqlca.sqlcode, sqlca.sqlerrm.sqlerrmc); 
      return EXIT_FAILURE;
   }
   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateCountableIncome()                            */
/*                                                                      */
/*  Description:     This will update t_re_ms_income whenever a non     */
/*                   zero value is received.                            */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO8509   10/10/2005 Lisa Salgat      Initial Release                */
/************************************************************************/
static int updateCountableIncome()
{
exec sql begin declare section;
char ms_prog_type[2+1];
int ms_dte_eff;
int ms_dte_end;
int ms_dte_eff_min1;
double ms_amt_income;             
int upd_ms_dte_eff = 0;
int upd_ms_dte_end;
double upd_ms_amt_income;             
exec sql end declare section;

int bIncomeUpdate = FALSE;
int bIncomeInsert = FALSE;
int ins_ms_dte_eff;
int ins_ms_dte_end;

   if (strncmp(prog_type, "MS", sizeof(prog_type)) == 0)
   {
      msIncome = msIncome/100;

      EXEC SQL DECLARE ms_income_csr CURSOR FOR
        SELECT kes_cde_prog_type,
               dte_effective,
               dte_end,
               amt_income,
               to_number(to_char(to_date(dte_effective, 'YYYYMMDD')-1, 'YYYYMMDD'))
          FROM t_re_ms_income
         WHERE sak_case          = :sak_case
           AND kes_cde_prog_type = :prog_type
        ORDER BY dte_effective;

      EXEC SQL OPEN ms_income_csr;
      if (sqlca.sqlcode != 0)
      {
         fprintf (stderr, "ERROR: could not open ms_income_csr \n");
         fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      EXEC SQL FETCH ms_income_csr
          INTO :ms_prog_type,
               :ms_dte_eff,
               :ms_dte_end,
               :ms_amt_income,
               :ms_dte_eff_min1;

      if (sqlca.sqlcode == 100)
      {
         if (insertCountableIncome(benefit_month_yyyymmdd, 22991231, msIncome, prog_type) != 0)
         {
            fprintf (stderr, "ERROR: bad return from insertCountableIncome\n");
            return EXIT_FAILURE;
         }
      }
      else if (sqlca.sqlcode != 0)
      {
         fprintf (stderr, "ERROR: could not fetch from t_re_ms_income table\n");
         fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }
      else
      {
         while (sqlca.sqlcode == 0 &&
                bIncomeUpdate == FALSE)
         {
            if (benefit_month_yyyymmdd < ms_dte_eff)
            {
               if (double_compare(msIncome, ms_amt_income) == 0    &&
                   strncmp(prog_type, ms_prog_type, sizeof(prog_type)) == 0)
               {
                  upd_ms_dte_eff     = benefit_month_yyyymmdd;
                  upd_ms_dte_end     = ms_dte_end;
                  upd_ms_amt_income  = ms_amt_income;
               }
               else
               {
                  if (insertCountableIncome(benefit_month_yyyymmdd, ms_dte_eff_min1, msIncome, prog_type) != 0)
                  {
                     fprintf (stderr, "ERROR: bad return from insertCountableIncome\n");
                     return EXIT_FAILURE;
                  }
               }
               bIncomeUpdate = TRUE;
            }

            if (benefit_month_yyyymmdd <= ms_dte_end &&
                benefit_month_yyyymmdd >= ms_dte_eff)
            {
               if (double_compare(msIncome, ms_amt_income) != 0)
               {
                  upd_ms_dte_eff     = benefit_month_yyyymmdd;
                  upd_ms_dte_end     = benefit_month_lastday;
                  upd_ms_amt_income  = msIncome;

                  if (benefit_month_yyyymmdd == ms_dte_eff)
                  {
                     if (benefit_month_lastday < ms_dte_end &&
                         ms_dte_end           != 22991231)
                     {
                        ins_ms_dte_eff = benefit_month_lastday_pls1;
                        ins_ms_dte_end = ms_dte_end;
                        bIncomeInsert = TRUE;
                     }
                     else
                     {
                        upd_ms_dte_end     = ms_dte_end;
                        bIncomeUpdate = TRUE;
                     }
                  }
                  else /* benefit_month_yyyymmdd > ms_dte_eff */
                  {
                     ins_ms_dte_eff = ms_dte_eff;
                     ins_ms_dte_end = benefit_month_min1day;
                     if (benefit_month_lastday < ms_dte_end)
                     {
                        if (insertCountableIncome(benefit_month_lastday_pls1, ms_dte_end, ms_amt_income, prog_type) != 0)
                        {
                           fprintf (stderr, "ERROR: bad return from insertCountableIncome\n");
                           return EXIT_FAILURE;
                        }
                     }

                     bIncomeInsert = TRUE;
                  }
               }

               bIncomeUpdate = TRUE;
            }

            if (bIncomeUpdate == FALSE)
            {
               EXEC SQL FETCH ms_income_csr
                   INTO :ms_prog_type,
                        :ms_dte_eff,
                        :ms_dte_end,
                        :ms_amt_income,
                        :ms_dte_eff_min1;
            }

         } /* end while */

         if (bIncomeUpdate == TRUE &&
             upd_ms_dte_eff > 0)
         {
            sqlca.sqlcode = 0;
            EXEC SQL
              UPDATE t_re_ms_income
                 SET dte_effective      = :upd_ms_dte_eff,
                     dte_end            = :upd_ms_dte_end,
                     amt_income         = :upd_ms_amt_income
               WHERE sak_case           = :sak_case
                AND  kes_cde_prog_type  = :ms_prog_type
                AND  amt_income         = :ms_amt_income
                AND  dte_effective      = :ms_dte_eff
                AND  dte_end            = :ms_dte_end;

           if (sqlca.sqlcode != 0)
           {
              fprintf (stderr, "ERROR: could not update t_re_ms_income table\n");
              fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
              fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
              return EXIT_FAILURE;
           }
         }
         if (bIncomeInsert == TRUE)
         {
            if (insertCountableIncome(ins_ms_dte_eff, ins_ms_dte_end, ms_amt_income, prog_type) != 0)
            {
               fprintf (stderr, "ERROR: bad return from insertCountableIncome\n");
               return EXIT_FAILURE;
            }
         }
      }

      EXEC SQL CLOSE ms_income_csr;

      if (sqlca.sqlcode != 0)
      {
         fprintf (stderr, "ERROR: could not fetch from t_re_ms_income table\n");
         fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }
   }

   return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertCountableIncome()                            */
/*                                                                      */
/*  Description:     Inserts new row into t_re_ms_income.               */
/*                                                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO8509   10/10/2005 Lisa Salgat      Initial Release                */
/************************************************************************/
static int insertCountableIncome(int effDte, int endDte, double effAmt, char effProgType[])
{
exec sql begin declare section;
int incEndDate = endDte;
int incEffDate = effDte;
double incMsAmount = effAmt;
char incProgType[2+1];
exec sql end declare section;

     memset(incProgType, '\0', sizeof(incProgType));
     strcpy(incProgType, effProgType);

     sqlca.sqlcode = 0;
     EXEC SQL
       INSERT INTO t_re_ms_income
              (sak_case,
               kes_cde_prog_type,
               dte_effective,
               dte_end,
               amt_income)
       VALUES (:bene_sak_case,
               :incProgType,
               :incEffDate,
               :incEndDate,
               :incMsAmount);

     if (sqlca.sqlcode != 0)
     {
        fprintf (stderr, "ERROR: could not insert into t_re_ms_income table\n");
        fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }

     return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   povPercent()                                       */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/*           03/03/2004 Jason Jobe       Added logic to process multiple*/
/*                                       records for same bene on       */
/*                                       same process date              */
/************************************************************************/
static int povPercent()
{

exec sql begin declare section;
int sak_pov_prcnt = 0,
     dte_eff,
     dte_end,
     elig_dte_end,
     pov_percent,
     dte_review,
     dte_cont_elig,
     open_date = OPEN_DATE;

char kes_cde_inv_med_sb[2+1];
exec sql end declare section;

    sqlca.sqlcode = 0;
    EXEC SQL
       SELECT sak_pov_prcnt,
              dte_effective,
              dte_end,
              pov_percent,
              kes_cde_inv_med_sb,
              dte_review,
              dte_cont_elig
       INTO   :sak_pov_prcnt,
              :dte_eff,
              :dte_end,
              :pov_percent,
              :kes_cde_inv_med_sb,
              :dte_review,
              :dte_cont_elig
       FROM   t_re_pov_prcnt
       WHERE  sak_recip       = :sak_recip
       AND    dte_end         = :open_date;

       if (sqlca.sqlcode == 100)
       {
          if (insertPovPrcnt(sak_recip) != 0)
          {
             fprintf (stderr, "ERROR: bad return from insertPovPrcnt\n");
             return EXIT_FAILURE;
          }
       }
       else if (sqlca.sqlcode == 0)
       {
          if ((dte_cont_elig != cont_elig) ||
              (dte_review != review_dte_yyyymmdd) ||
              (memcmp(kes_cde_inv_med_sb, med_subtype, sizeof(med_subtype)) != 0) ||
              (pov_percent != percent))
          {
             sqlca.sqlcode = 0;
             EXEC SQL
                 SELECT MAX(dte_end)
                 INTO   :elig_dte_end
                 FROM   t_re_aid_elig
                 WHERE  sak_recip        = :sak_recip
                 AND    kes_cde_med_type = 'MP'
                 AND    dte_active_thru  = :open_date
                 AND    cde_status1      = ' ';

             if (sqlca.sqlcode == 0)
             {
                if (elig_dte_end <= benefit_month_lastday)
                {
                   if (updatePovPrcnt(sak_pov_prcnt,dte_eff) != 0)
                   {
                      fprintf (stderr, "ERROR: bad return from updatePovPrcnt\n");
                      return EXIT_FAILURE;
                   }

                  if( dte_eff < curr_dte )
                  {
                    if (insertPovPrcnt(sak_recip) != 0)
                    {
                       fprintf (stderr, "ERROR: bad return from insertPovPrcnt\n");
                       return EXIT_FAILURE;
                    }
                  }
                }
             }
             else
             {
                fprintf (stderr, "ERROR: could not select max dte_end from t_re_elig table\n");
                fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
                fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
                return EXIT_FAILURE;
             }
          }
       }
       else
       {
          fprintf (stderr, "ERROR: could not select from Poverty table\n");
          fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }

       return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   respAddress()                                      */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11849  04/04/2009 Srini Donepudi   Initial Release                */
/************************************************************************/
static int respAddress()
{
exec sql begin declare section;
 
int r_sak_short = 0, 
     r_dte_effective = 0,
     open_date = OPEN_DATE;
char r_nam_first[12+1]; 
char r_nam_last[17+1]; 
char r_nam_middle[1+1]; 
char r_adr_street_1[30+1];
char r_adr_street_2[30+1];
char r_adr_city[18+1];
char r_adr_state[2+1];
char r_adr_zip[5+1];
char r_adr_zip4[4+1];
char r_num_phone[10+1];

exec sql end declare section;

    sqlca.sqlcode = 0;
    EXEC SQL
       SELECT sak_short,
              nam_first, 
              nam_last, 
              nam_middle,  
              adr_street_1,
              adr_street_2,
              adr_city,
              adr_state, 
              adr_zip_code,
              adr_zip_code_4,
              num_phone,
              dte_effective
       INTO   :r_sak_short,
              :r_nam_first,
              :r_nam_last,
              :r_nam_middle, 
              :r_adr_street_1,
              :r_adr_street_2,
              :r_adr_city,
              :r_adr_state,
              :r_adr_zip,
              :r_adr_zip4,
              :r_num_phone,
              :r_dte_effective
       FROM   t_re_resp_person
       WHERE  sak_recip       = :sak_recip
       AND    dte_end         = :open_date;

       if (sqlca.sqlcode == 100)
       {
          if (insertRespAddress() != 0)
          {
             fprintf (stderr, "ERROR: bad return from insertRespAddress.\n");
             return EXIT_FAILURE;
          }
       }
       else if (sqlca.sqlcode == 0)
       {
          if ((strncmp(r_nam_first, rp_nam_first, sizeof(r_nam_first)) != 0) ||
              (strncmp(r_nam_last, rp_nam_last, sizeof(r_nam_last)) != 0) ||
              (strncmp(r_nam_middle, rp_nam_mid, sizeof(r_nam_middle)) != 0) ||
              (strncmp(r_adr_street_1, resp_adr_street1, 25) != 0) ||
              (strncmp(r_adr_street_2, resp_adr_street2, 25) != 0) ||
              (strncmp(r_adr_city, resp_adr_city, 15) != 0) ||
              (strncmp(r_adr_state, resp_adr_state, sizeof(resp_adr_state)) != 0) ||
              (strncmp(r_adr_zip, resp_adr_zip_code, sizeof(resp_adr_zip_code)) != 0) ||
              (strncmp(r_adr_zip4, resp_adr_zip_code_4, sizeof(r_adr_zip4)) != 0) )
          {

              if (updateRespAddress(r_sak_short,r_dte_effective) != 0)
              {
                  fprintf (stderr, "ERROR: bad return from updateRespAddress\n");
                  return EXIT_FAILURE;
              }
              if (insertRespAddress() != 0)
              {
                 fprintf (stderr, "ERROR: bad return from insertRespAddress.\n");
                 return EXIT_FAILURE;
              }
          }
       }
       else
       {
          fprintf (stderr, "ERROR: could not select from t_re_resp_person table\n");
          fprintf (stderr, "ERROR: SakRecip [%d], ID Medicaid [%s]\n", sak_recip, id_medicaid);
          fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }

       return EXIT_SUCCESS;

}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updateRespAddress()                                */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11849  04/04/2009 Srini Donepudi   Initial Release                */
/************************************************************************/
static int updateRespAddress(int sakShort, int eff_date )
{
exec sql begin declare section;
int r_sak_short;
int r_dte_eff;
int new_dte_end = OPEN_DATE;
exec sql end declare section;

     r_sak_short = sakShort;
     r_dte_eff = eff_date;
     sqlca.sqlcode = 0;

     if ( r_dte_eff >= curr_dte )
     {
       EXEC SQL
       DELETE FROM t_re_resp_person
       WHERE  sak_recip = :sak_recip     
         AND  sak_short = :r_sak_short; 
     }
     else
     {
       EXEC SQL
       UPDATE t_re_resp_person 
       SET    dte_end = :curr_dte_min1day
       WHERE  sak_recip = :sak_recip
         AND  sak_short = :r_sak_short;
     }

    if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
    {
         fprintf (stderr, "ERROR: could not update/delete resp person table\n");
         fprintf (stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
     }

     return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertRespAddress()                                */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO11849  04/04/2009 Srini Donepudi   Initial Release                */
/************************************************************************/
static int insertRespAddress()
{
exec sql begin declare section;
int open_date = OPEN_DATE;
int r_sak_short = 0;
exec sql end declare section;

   sqlca.sqlcode = 0;
   EXEC SQL
       SELECT nvl(max(sak_short),0)
       INTO   :r_sak_short
       FROM   t_re_resp_person
       WHERE sak_recip = :sak_recip;

      if (sqlca.sqlcode != 0)
      {
         fprintf(stderr, "ERROR: could not select max sak short from t_re_resp_person\n");
         fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
         fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
      }

      r_sak_short++;

   EXEC SQL
     INSERT INTO t_re_resp_person
            (sak_recip,
             sak_short,
             nam_first,
             nam_last,
             nam_middle,
             adr_street_1,
             adr_street_2,
             adr_city,
             adr_state,
             adr_zip_code,
             adr_zip_code_4,
             num_phone,
             dte_effective,
             dte_end)
      VALUES (:sak_recip,
              :r_sak_short,
              :rp_nam_first,
              :rp_nam_last,
              :rp_nam_mid,
              :resp_adr_street1,
              :resp_adr_street2,
              :resp_adr_city,
              :resp_adr_state,
              :resp_adr_zip_code,
              :resp_adr_zip_code_4,
              '          ',
              :curr_dte,
              :open_date);

       if (sqlca.sqlcode != 0)
       {
          fprintf (stderr, "ERROR: could not insert into t_re_resp_person table\n");
          fprintf (stderr, "ERROR: SakRecip [%d], ID Medicaid [%s]\n", sak_recip,id_medicaid);
          fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }

       return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   updatePovPrcnt()                                   */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int updatePovPrcnt(int sak, int eff_date )
{
exec sql begin declare section;
int sak_pov_prcnt;
int dte_eff;
int new_dte_end = OPEN_DATE;
exec sql end declare section;

     sak_pov_prcnt = sak;
     dte_eff = eff_date;
     sqlca.sqlcode = 0;

     if ( dte_eff >= curr_dte )
     {

       EXEC SQL
       UPDATE t_re_pov_prcnt
       SET    dte_end = :new_dte_end,
              pov_percent =:percent,
              kes_cde_inv_med_sb =:med_subtype,
              dte_review =:review_dte_yyyymmdd,
              dte_cont_elig =:cont_elig,
              amt_net_income =:net_income
       WHERE  sak_pov_prcnt = :sak_pov_prcnt;
     }
     else
     {
       EXEC SQL
       UPDATE t_re_pov_prcnt
       SET    dte_end = :curr_dte_min1day
       WHERE  sak_pov_prcnt = :sak_pov_prcnt;
     }

    if ((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
       {
          fprintf (stderr, "ERROR: could not update Poverty table\n");
          fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }

       return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertPovPrcnt()                                   */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int insertPovPrcnt(int bene_sak)
{
exec sql begin declare section;
int open_date = OPEN_DATE;

int pov_prcnt_sak_recip;
exec sql end declare section;

    if (getNewSakPovPrcnt() != 0)
    {
       fprintf (stderr, "ERROR: bad return from getNewSakPovPrcnt\n");
       return EXIT_FAILURE;
    }

    pov_prcnt_sak_recip = bene_sak;

   sqlca.sqlcode = 0;
   EXEC SQL
     INSERT INTO t_re_pov_prcnt
            (sak_pov_prcnt,
             sak_recip,
             dte_effective,
             dte_end,
             pov_percent,
             kes_cde_inv_med_sb,
             dte_review,
             dte_cont_elig,
             amt_net_income)
      VALUES (:new_sak_pov_prcnt,
              :pov_prcnt_sak_recip,
              :curr_dte,
              :open_date,
              :percent,
              :med_subtype,
              :review_dte_yyyymmdd,
              :cont_elig,
              :net_income);

    if (sqlca.sqlcode != 0)
       {
          fprintf (stderr, "ERROR: could not insert into Poverty table\n");
          fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
          fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
          return EXIT_FAILURE;
       }

       return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   calBenefitMth()                                    */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int calBenefitMth()
{
     sqlca.sqlcode = 0;
     EXEC SQL
        SELECT to_char(last_day(to_date(:benefit_month_yyyymmdd, 'YYYYMMDD')), 'YYYYMMDD'),
               to_char(last_day(to_date(:benefit_month_yyyymmdd, 'YYYYMMDD'))+1, 'YYYYMMDD'),
               to_number(to_char(to_date(:benefit_month_yyyymm||01, 'YYYYMMDD')-1, 'YYYYMMDD'))

        INTO :benefit_month_lastday,
             :benefit_month_lastday_pls1,
             :benefit_month_min1day
        FROM dual;

        if (sqlca.sqlcode != 0)
        {
           fprintf (stderr, "ERROR: could not retrieve last day of the month\n");
           fprintf(stderr, "ERROR: ID Medicaid %s\n", id_medicaid);
           fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
        }

        orig_benefit_month              = benefit_month_yyyymmdd;
        orig_benefit_month_lastday      = benefit_month_lastday;
        orig_benefit_month_lastday_pls1 = benefit_month_lastday_pls1;
        orig_benefit_month_min1day      = benefit_month_min1day;

        return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   insertErrTbl()                                     */
/*                                                                      */
/*  Description:    Insert errors into the t_re_err table.              */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int insertErrTbl(int cde)
{

exec sql begin declare section;
int code_error;
exec sql end declare section;

   code_error = cde;
   sv_error = code_error;

   sqlca.sqlcode = 0;
   EXEC SQL
       INSERT INTO t_re_err
              (id_medicaid,
               nam_last,
               nam_first,
               nam_mid_init,
               num_ssn,
               dte_birth,
               nam_field,
               dsc_field,
               cde_error,
               num_case,
               benefit_month,
               id_case_worker,
               dte_tme_stamp,
               cde_txn_type)
       VALUES  (:client_number,
                :nam_last,
                :nam_first,
                :nam_mid,
                :base_num_ssn,
                :base_dte_birth,
                :name_field,
                :desc_field,
                :code_error,
                :ksc_case_number,
                :benefit_month_yyyymm,
                :worker,
                :dte_time_stamp,
                'ELIG');

	if (sqlca.sqlcode != 0)
	{
           fprintf (stderr, "ERROR: could not insert into t_re_err table\n");
           fprintf (stderr, "ERROR: Id Medicaid %s\n", id_medicaid);
           fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
	}

    return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:  insertPeRevTxn()                                    */
/*                                                                      */
/*  Description:    Inserts errored off P19 or P21 records into the     */
/*                  t_re_pe_rev_txn table                               */
/************************************************************************/
/*  REF #  DATE       AUTHOR             DESCRIPTION                    */
/*  -----  ---------  -----------------  -----------------------------  */
/*  CO9489 05/14/2005 Srinivas Donepudi  Initial Release                */
/************************************************************************/ 
static int insertPeRevTxn(char pecase[], int pemth, int pepubhlth, int peeffdate, char peindspnddown[], char pecdeerr[])
{
   exec sql begin declare section;
    int pe_sak_recip;
    short pe_sak_pgm_elig;
    char pe_num_case[10+1];
    int pe_mth_benefit = pemth;
    int pe_sak_pub_hlth = pepubhlth;
    int pe_dte_effective = peeffdate;
    char pe_ind_spenddown[1+1];
    char pe_cde_error[4+1];
   exec sql end declare section;
 
   memset(pe_num_case, '\0', sizeof(pe_num_case));
   strcpy(pe_num_case, pecase);
 
   memset(pe_ind_spenddown, '\0', sizeof(pe_ind_spenddown));
   strcpy(pe_ind_spenddown, peindspnddown);

   memset(pe_cde_error, '\0', sizeof(pe_cde_error));
   strcpy(pe_cde_error, pecdeerr);
  
   if (sak_recip > 0)
   { 
      pe_sak_recip = sak_recip;
   }
   else
   {
      sqlca.sqlcode = 0;
      EXEC SQL
        SELECT sak_recip
        INTO   :pe_sak_recip
        FROM  t_re_base
        WHERE id_medicaid = :id_medicaid;

      if((sqlca.sqlcode != 0) && (sqlca.sqlcode != 100))
      {
        fprintf(stderr,"ERROR: could not select sak recip from t_re_base\n");
        fprintf(stderr,"ERROR: ID Medicaid %s\n", id_medicaid);
        fprintf(stderr,"ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
      }
   } 
  
   sqlca.sqlcode = 0;
 
   EXEC SQL
        SELECT nvl(max(sak_pgm_elig),0)
          INTO :pe_sak_pgm_elig
          FROM t_re_pe_rev_txn
         WHERE sak_recip = :pe_sak_recip;

          pe_sak_pgm_elig++;

          if (sqlca.sqlcode != 0)
          {
             fprintf(stderr,"ERROR: could not select max sak_pgm_elig from t_re_pe_rev_txn\n");
             fprintf(stderr,"ERROR: sak_recip %d\n", sak_recip);
             fprintf(stderr,"ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
             return EXIT_FAILURE;
          }
 
   EXEC SQL
       INSERT INTO t_re_pe_rev_txn
              (sak_recip,
               sak_pgm_elig,
               num_case,
               mth_benefit,
               sak_pub_hlth,
               dte_effective,
               ind_spenddown,
               cde_error,
               dte_processed,
               ind_action,
               dte_action,
               id_clerk)
       VALUES  (:pe_sak_recip,
                :pe_sak_pgm_elig,
                :pe_num_case,
                :pe_mth_benefit,
                :pe_sak_pub_hlth,
                :pe_dte_effective,
                :pe_ind_spenddown,
                :pe_cde_error,
                :curr_dte,
                ' ',
                0,
                '        ');

        if (sqlca.sqlcode != 0)
        {
           fprintf (stderr, "ERROR: could not insert into t_re_pe_rev_txn table\n");
           fprintf (stderr, "ERROR: sak_recip %d\n", sak_recip);
           fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
        }

    return EXIT_SUCCESS; 
 
}

static int createTmpPeRevTxn()
{
     sqlca.sqlcode = 0;
  
     EXEC SQL DROP TABLE t_re_tmp_pe_rev_txn;

     EXEC SQL
          CREATE TABLE t_re_tmp_pe_rev_txn (
                       id_medicaid char(11) not null,
                       num_case    char(10) not null,
                       mth_benefit number(6) not null,
                       sak_pub_hlth number(9) not null,
                       dte_effective number(8) not null,
                       ind_spenddown char(1) not null,
                       cde_error char(4) not null,
                       dte_processed number(8) not null);
                       
     if (sqlca.sqlcode != 0)
     {
         fprintf (stderr, "ERROR: could not create t_re_tmp_pe_rev_txn\n");
         fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
     }

     return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:  insertTmpPeRevTxn()                                 */
/*                                                                      */
/*  Description:    Inserts errored off P19 or P21 records into the     */
/*                  t_re_tmp_pe_rev_txn table                           */
/************************************************************************/
/*  REF #  DATE       AUTHOR             DESCRIPTION                    */
/*  -----  ---------  -----------------  -----------------------------  */
/*  CO9489 05/14/2005 Srinivas Donepudi  Initial Release                */
/************************************************************************/
static int insertTmpPeRevTxn(char pecase[], int pemth, int pepubhlth, int peeffdate, char peindspnddown[], char pecdeerr[])
{
  
  exec sql begin declare section;
    char pe_num_case[10+1];
    int pe_mth_benefit = pemth;
    int pe_sak_pub_hlth = pepubhlth;
    int pe_dte_effective = peeffdate;
    char pe_ind_spenddown[1+1];
    char pe_cde_error[4+1];
  exec sql end declare section;

   memset(pe_num_case, '\0', sizeof(pe_num_case));
   strcpy(pe_num_case, pecase);

   memset(pe_ind_spenddown, '\0', sizeof(pe_ind_spenddown));
   strcpy(pe_ind_spenddown, peindspnddown);

   memset(pe_cde_error, '\0', sizeof(pe_cde_error));
   strcpy(pe_cde_error, pecdeerr);

   EXEC SQL
       INSERT INTO t_re_tmp_pe_rev_txn
              (id_medicaid,
               num_case,
               mth_benefit,
               sak_pub_hlth,
               dte_effective,
               ind_spenddown,
               cde_error,
               dte_processed)
       VALUES  (:id_medicaid,
                :pe_num_case,
                :pe_mth_benefit,
                :pe_sak_pub_hlth,
                :pe_dte_effective,
                :pe_ind_spenddown,
                :pe_cde_error,
                :curr_dte);

     if (sqlca.sqlcode != 0)
     {
        fprintf (stderr, "ERROR: could not insert into t_re_tmp_pe_rev_txn table\n");
        fprintf (stderr, "ERROR: sak_recip %d\n", sak_recip);
        fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }

    return EXIT_SUCCESS;
} 

/************************************************************************/
/*                                                                      */
/*  Function Name:  processTmpPeRevTxn()                                */
/*                                                                      */
/*  Description:    Inserts errored off P19 or P21 records into the     */
/*                  t_re_pe_rev_txn table from t_re_tmp_pe_rev_table    */
/************************************************************************/
/*  REF #  DATE       AUTHOR             DESCRIPTION                    */
/*  -----  ---------  -----------------  -----------------------------  */
/*  CO9489 05/14/2005 Srinivas Donepudi  Initial Release                */
/************************************************************************/
static int processTmpPeRevTxn()
{
  exec sql begin declare section;
    int pe_sak_recip;
   short pe_sak_pgm_elig;
    char pe_num_case[10+1];
    int pe_mth_benefit;
    int pe_sak_pub_hlth;
    int pe_dte_effective;
    char pe_ind_spenddown[1+1];
    char pe_cde_error[4+1];
    int pe_dte_processed;
  exec sql end declare section;

  int csr_rc = 0;
 
    EXEC SQL DECLARE tmp_pe_rev_txn_csr CURSOR FOR
             SELECT sak_recip,
                    num_case,
                    mth_benefit,
                    sak_pub_hlth,
                    dte_effective,
                    ind_spenddown,
                    cde_error,
                    dte_processed
               FROM t_re_tmp_pe_rev_txn a,
                    t_re_base b
              WHERE a.id_medicaid = b.id_medicaid
              ORDER BY sak_recip,mth_benefit,dte_processed; 
          
     EXEC SQL OPEN tmp_pe_rev_txn_csr;
  
     if (sqlca.sqlcode != 0)
     {
        fprintf(stderr, "ERROR: could not open tmp_pe_rev_txn_csr\n");
        fprintf(stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
        return EXIT_FAILURE;
     }
    
     csr_rc = sqlca.sqlcode;
 
     while (csr_rc == 0)
     {
        EXEC SQL FETCH tmp_pe_rev_txn_csr
                  INTO :pe_sak_recip,
                       :pe_num_case,
                       :pe_mth_benefit,
                       :pe_sak_pub_hlth,
                       :pe_dte_effective,
                       :pe_ind_spenddown,
                       :pe_cde_error,
                       :pe_dte_processed;
 
         csr_rc = sqlca.sqlcode; 
     
         if (csr_rc == 0)
         {
             EXEC SQL
                 SELECT nvl(max(sak_pgm_elig),0)
                   INTO :pe_sak_pgm_elig
                   FROM t_re_pe_rev_txn
                  WHERE sak_recip = :pe_sak_recip;

             pe_sak_pgm_elig++;

             if (sqlca.sqlcode != 0)
             {
                fprintf(stderr,"ERROR: could not select max sak_pgm_elig from t_re_pe_rev_txn\n");
                fprintf(stderr,"ERROR: sak_recip %d\n", pe_sak_recip);
                fprintf(stderr,"ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
                return EXIT_FAILURE;
             }

             EXEC SQL
                  INSERT INTO t_re_pe_rev_txn
                              (sak_recip,
                               sak_pgm_elig,
                               num_case,
                               mth_benefit,
                               sak_pub_hlth,
                               dte_effective,
                               ind_spenddown,
                               cde_error,
                               dte_processed,
                               ind_action,
                               dte_action,
                               id_clerk)
                      VALUES  (:pe_sak_recip,
                               :pe_sak_pgm_elig,
                               :pe_num_case,
                               :pe_mth_benefit,
                               :pe_sak_pub_hlth,
                               :pe_dte_effective,
                               :pe_ind_spenddown,
                               :pe_cde_error,
                               :pe_dte_processed,
                               ' ',
                               0,
                               '        ');
               
            if (sqlca.sqlcode != 0)
            {
               fprintf (stderr, "ERROR: could not insert into t_re_pe_rev_txn table\n");
               fprintf (stderr, "ERROR: sak_recip [%d] [%d] [%s] [%d] [%d] [%d] [%s] [%s] [%d] \n", 
                                 pe_sak_recip, pe_sak_pgm_elig, pe_num_case, pe_mth_benefit ,pe_sak_pub_hlth ,pe_dte_effective,
                                 pe_ind_spenddown, pe_cde_error, pe_dte_processed);
               fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
               return EXIT_FAILURE;
            } 
         }
    
     }
 
     if ((csr_rc != 0) && (csr_rc != 100))
     {
         fprintf (stderr, "ERROR: could not fetch from t_re_tmp_pe_rev_txn table\n");
         fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
         return EXIT_FAILURE;
     }

      /*Commit all modified rows*/
      if (commet_it() != 0)
      {
         fprintf (stderr, "ERROR: could not commit (3)\n");
         return EXIT_FAILURE;
      }

     return EXIT_SUCCESS; 
}
 
/************************************************************************/
/*                                                                      */
/*  Function Name:   inputCount()                                       */
/*                                                                      */
/*  Description:    Inputs the total number of modified rows into the   */
/*                  summary table.                                      */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int inputCount()
{

     sqlca.sqlcode = 0;
     EXEC SQL
       UPDATE t_re_txn_sum
       SET num_accepted = :num_accept,
           num_rejected = :num_reject
       WHERE cde_txn_type = 'ELIG';

	if (sqlca.sqlcode != 0)
	{
           fprintf (stderr, "ERROR: could not update t_re_txn_sum\n");
           fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
           return EXIT_FAILURE;
	}

     return EXIT_SUCCESS;
}
/************************************************************************/
/*                                                                      */
/*  Function Name:   commet_it()                                        */
/*                                                                      */
/*  Description:                                                        */
/************************************************************************/
/*  REF #    DATE       AUTHOR           DESCRIPTION                    */
/*  -----    ---------  ---------------  -----------------------------  */
/*  CO1305   01/04/2003 Koren Allen      Initial Release                */
/************************************************************************/
static int commet_it()
{

   sqlca.sqlcode = 0;
   EXEC SQL
     COMMIT;

    if (sqlca.sqlcode != 0)
    {
       fprintf (stderr, "ERROR: could not commit (4)\n");
       fprintf (stderr, "ERROR: Sak recip %d\n", sak_recip);
       fprintf (stderr, "ERROR: sqlca.sqlcode %d\n", sqlca.sqlcode);
       return EXIT_FAILURE;
    }

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   update_t_patm()                                    */
/*                                                                      */
/*  Description: Called to update the parm that managed care uses to    */
/*               tell that the monthly KAECSES process has run.         */
/*                                                                      */
/************************************************************************/
static int  update_t_patm()
{
exec sql begin declare section;
int lParm1;
int lParm2;
exec sql end declare section;

    EXEC SQL
         SELECT date_parm_1,
                dte_parm_2
         INTO   :lParm1,
                :lParm2
         FROM   t_system_parms
         WHERE  nam_program = 'KCSESLRM';

    if (sqlca.sqlcode != 0)
    {
       fprintf (stderr, "ERROR: could not select KAECSES last run dates for MC\n");
       return EXIT_FAILURE;
    }

    EXEC SQL
         UPDATE  t_system_parms
            SET  DATE_PARM_1  = :lParm1,
                 DTE_PARM_2   = :lParm2
          WHERE  nam_program  = 'KCSESPRM';

    if (sqlca.sqlcode != 0 && sqlca.sqlcode != 100 )
    {
       fprintf (stderr, "ERROR: could not update previous KAECSES run dates for MC\n");
       return EXIT_FAILURE;
    }

    EXEC SQL
         UPDATE  t_system_parms
            SET  DATE_PARM_1  = (to_number(to_char(SYSDATE, 'YYYYMMDD'))),
                 DTE_PARM_2  = (to_number(to_char(add_months(sysdate,1),'YYYYMM')|| '01'))
          WHERE  nam_program  = 'KCSESLRM';

    if (sqlca.sqlcode != 0 && sqlca.sqlcode != 100 )
    {
       fprintf (stderr, "ERROR: could not update ID Card date parm\n");
       return EXIT_FAILURE;
    }

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   getNewSakRecip()                                   */
/*                                                                      */
/*  Description:     Reserves the next sak for insert.                  */
/*                                                                      */
/************************************************************************/
static int  getNewSakRecip()
{
   if (svchp == NULL)
      ociConnect( "AIM_PSWD");

   new_sak_recip = sakIncrement(svchp, "SAK_RECIP", "T_RE_BASE", TRUE);

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   getNewSakCase()                                    */
/*                                                                      */
/*  Description:     Reserves the next sak for insert.                  */
/*                                                                      */
/************************************************************************/
static int  getNewSakCase()
{
   if (svchp == NULL)
      ociConnect( "AIM_PSWD");

   new_sak_case = sakIncrement(svchp, "SAK_CASE", "T_RE_CASE", TRUE);

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   getNewSakCaseXref()                                */
/*                                                                      */
/*  Description:     Reserves the next sak for insert.                  */
/*                                                                      */
/************************************************************************/
static int  getNewSakCaseXref()
{
   if (svchp == NULL)
      ociConnect( "AIM_PSWD");

   new_sak_case_xref = sakIncrement(svchp, "SAK_CASE_XREF", "T_RE_CASE_XREF", TRUE);

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   getNewSakLink()                                    */
/*                                                                      */
/*  Description:     Reserves the next sak for insert.                  */
/*  Note:            This isn't currently used                          */
/************************************************************************/
static int  getNewSakLink()
{
   if (svchp == NULL)
      ociConnect( "AIM_PSWD");

   new_sak_link = sakIncrement(svchp, "SAK_LINK", "T_RECIP_LINK_XREF", TRUE);

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   getNewSakAidElig()                                 */
/*                                                                      */
/*  Description:     Reserves the next sak for insert.                  */
/*                                                                      */
/************************************************************************/
static int  getNewSakAidElig()
{
   if (svchp == NULL)
      ociConnect( "AIM_PSWD");

   new_sak_aid_elig = sakIncrement(svchp, "SAK_AID_ELIG", "T_RE_AID_ELIG", TRUE);

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   getNewSakPovPrcnt()                                */
/*                                                                      */
/*  Description:     Reserves the next sak for insert.                  */
/*                                                                      */
/************************************************************************/
static int  getNewSakPovPrcnt()
{
   if (svchp == NULL)
      ociConnect( "AIM_PSWD");

   new_sak_pov_prcnt = sakIncrement(svchp, "SAK_POV_PRCNT", "T_RE_POV_PRCNT", TRUE);

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   getNewSakSpendLiab()                               */
/*                                                                      */
/*  Description:     Reserves the next sak for insert.                  */
/*                                                                      */
/************************************************************************/
static int  getNewSakSpendLiab()
{
   if (svchp == NULL)
      ociConnect( "AIM_PSWD");

   new_sak_spend_liab = sakIncrement(svchp, "SAK_SPEND_LIB", "T_RE_SPEND_LIAB", TRUE);

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   getNewSakRecIdCard()                               */
/*                                                                      */
/*  Description:     Reserves the next sak for insert.                  */
/*  Note:            This isn't currently used                          */
/************************************************************************/
static int  getNewSakRecIdCard()
{
   if (svchp == NULL)
      ociConnect( "AIM_PSWD");

   new_sak_rec_id_card = sakIncrement(svchp, "SAK_REC_ID_CARD", "T_RE_ID_CARD", TRUE);

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   getNewSakAlert()                                   */
/*                                                                      */
/*  Description:     Reserves the next sak for insert.                  */
/*  Note:            This isn't currently used                          */
/************************************************************************/
static int  getNewSakAlert()
{
   if (svchp == NULL)
      ociConnect( "AIM_PSWD");

   new_sak_alert = sakIncrement(svchp, "SAK_ALERT", "T_CDE_ALERT", TRUE);

   return EXIT_SUCCESS;
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   getNewSakPremium()                                 */
/*                                                                      */
/*  Description:     Reserves the next sak for insert.                  */
/*                                                                      */
/************************************************************************/
static int  getNewSakPremium()
{
   if (svchp == NULL)
      ociConnect( "AIM_PSWD");

   new_sak_premium = sakIncrement(svchp, "SAK_PREMIUM", "T_RE_PREMIUM", TRUE);

   return EXIT_SUCCESS;
}


