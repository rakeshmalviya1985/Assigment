#pragma ident "@(#)mgd_autoassign_kc.sc	1.48 05/31/16 EDS"
/********************************************************************************/
/*                                                                              */
/*   PROGRAM:     mgd_autoassign_kc                                             */
/*                                                                              */
/*   DESCRIPTION: This program does many different types of                     */
/*                managed care assignments for beneficiaries on the             */
/*                potential table that have been identified as                  */
/*                eligible for managed care.                                    */
/*                                                                              */
/********************************************************************************/
/*                                                                              */
/*                     Modification Log                                         */
/*                                                                              */
/* Date     CO       Author        Description                                  */
/* -------- ------   ----------    -----------------                            */
/* 12/18/12          Rob Lohrke    Initial implementation for KanCare           */
/* 01/15/14  14929   Doug Hawkins  Initialized variable to fix random pop code  */
/*                                 problem.                                     */
/*                                 NOTE: This version is reverting back to 1.33.*/
/*                                 There were modifications made to this file   */
/*                                 pertaining to KEES which could not be        */
/*                                 promoted to PROD, and this fix needed to go. */
/* 12/05/13  -----   Rob Lohrke    Misc fixes.                                  */
/* 01/29/14  14929   Doug Hawkins  Fixed issue in make_pmp_assgn() which was    */
/*                                 using a pop code of zero instead of the      */
/*                                 correct value it found in the retry loop.    */
/* 01/10/14  14929   Doug Hawkins  Fix for overlapping assignments issue.       */
/* 02/04/14  14929/  Doug Hawkins  This version implemented all non-KEES fixes, */
/*           15303                 and uses a system parm to execute KEES logic.*/
/*       **NOTE: for any ongoing KEES logic, use UseKEES_Logic flag.            */
/* 02/21/14  15303  Doug Hawkins   KEES fix: when presumptive and NARE          */
/*                                 processing stops a month from being used for */
/*                                 assignment, needed to inhibit that month's   */
/*                                 potential add date from being used.          */
/*                                                                              */
/* 08/22/14  15817  Doug Hawkins   T21 newborns need to use DOB, not FOM.       */
/* 10/09/14  15817  Doug Hawkins   T21 newborns need to use elig date.          */
/* 10/21/14  15817  Doug Hawkins   P19 newborns need to use elig date too.      */
/* 11/24/14  15817  Doug Hawkins   Newborns fix.                                */
/* 03/17/16  15502  Rob Lohrke     Modified main().                             */
/* 05/24/16  15545  Doug Hawkins   Added missing sak_mc_entity values.          */
/********************************************************************************/
                          //If presumptive and the potential add date month is in the same month as the current record, then need to flag
                                                    //the potential date so it won't be used.
                          
/*********************************************************************/
/*                         Includes                                  */
/*********************************************************************/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include <errno.h>

#include "mgd_common.h"
#include "mgd_autoassign.h"
#include "hmspconn.h"

/************************************************************************/
/*                  Macro Definitions (Constants)                       */
/************************************************************************/

/* #define DBG */

#define PROGNAME          "mgd_autoassign_kc_init"
#define SPACE                   ' '
#define ZERO                    '0'
#define MAX_STRING              (60)

#define HMO_ADD_LTR             1
#define HMO_REINSTATE_LTR       2
#define PCPCM_REINSTATE_LTR     3
#define PCPCM_ADD_LTR           4
#define RECIP_CHSN_PLAN_FULL    5
#define MCO_DISENROLLED         6
#define PCPCM_DISENROLLED       7


// The following descriptions must fit into a field that is no more than 50+1 bytes...
#define NBNOPOTMOM "NO POTENTIAL MOTHERS WITH ASSIGNMENT DATA"
#define NBMULTIPOTMOM  "MOTHER(S) WITH DIFFERENT/NO ASSIGNMENTS"
#define NBMULTIMOMSDIFFASSIGNS "MORE THAN ONE PNTL MOTHER WITH DIFF ASSIGNMENTS"
#define NBHCKPOTMOM "POTENTIAL MOTHER(S) WITH HCK ASSIGNMENT"
#define NBNOTELIGMB "NOT ELIGIBLE FOR MONTH OF BIRTH"
#define NBNOTNEWELG "ELIGIBILITY IS NOT A VIRGIN ELIGIBILITY"  //more than one potential mother with different assignments
#define NBNOTELIGCM "NOT ELIGIBLE FOR CURRENT MONTH"
#define NEWBORNRPTIND "N"



/************************************************************************/
/* If you need to display the built-in debugging messages, uncomment    */
/* the following                                                        */
/************************************************************************/
/* #define DEBUG */


/************************************************************************/
/*                  Type Declarations and Internal Structures           */
/************************************************************************/
struct re_mc_recip_remo
{
    int sak_recip;                 /* NUMBER(9)    */
    int sak_pub_hlth;              /* NUMBER(9)    */
    int dte_added;                 /* NUMBER(8)    */
    char cde_reason[1+1];          /* CHAR(1)      */
    char cde_rsn_mc_det[1+1];      /* CHAR(1)      */
    char cde_rsn_del[1+1];         /* CHAR(1)      */
    int sak_pmp_transfer;
    int dte_transfer_start;
    int sak_mc_ent_add;
    char cde_morbidity[1+1];
} ;

struct kc_elig
{
    short sak_short;
    int dte_benefit_month;
};


/*********************************************************************/
/*                         DB Variables                              */
/*********************************************************************/
EXEC SQL INCLUDE SQLCA;


EXEC SQL BEGIN DECLARE SECTION;

    static long    sql_mco_choice_last_run_datetime = 0;
    static int     sql_mco_choice_last_run_date = 0;
    static int     sql_mco_choice_last_run_time = 0;
    static long    sql_kc_elig_last_run_datetime = 0;
    static int     sql_kc_elig_last_run_date = 0;
    static int     sql_kc_elig_last_run_time = 0;

    static int TXIX_SAK_PUB_HLTH =      1;
    static int MN_SAK_PUB_HLTH =        3;
    static int TXXI_SAK_PUB_HLTH =      4;
    static int P19_SAK_PUB_HLTH =       22;
    static int P21_SAK_PUB_HLTH =       23;

    static char    UNITED_PRAP_SQL[] = "K3";
    static int     UNITED_SAK_PROV_SQL = 20086989;
    static int     UNITED_T19_SAK_PMP_SER_LOC_SQL = 5003;
    static int     UNITED_T21_SAK_PMP_SER_LOC_SQL = 5004;

    static char    SUNFLOWER_PRAP_SQL[] = "K2";
    static int     SUNFLOWER_SAK_PROV_SQL = 20087650;
    static int     SUNFLOWER_T19_SAK_PMP_SER_LOC_SQL = 5006;
    static int     SUNFLOWER_T21_SAK_PMP_SER_LOC_SQL = 5007;

    static char    AMERIGROUP_PRAP_SQL[] = "K1";
    static int     AMERIGROUP_SAK_PROV_SQL = 20086974;
    static int     AMERIGROUP_T19_SAK_PMP_SER_LOC_SQL = 5008;
    static int     AMERIGROUP_T21_SAK_PMP_SER_LOC_SQL = 5009;

    static char    UNSELECTED_PRAP_SQL[] = "K4";

    static struct re_mc_recip_remo sql_pntl_rcd;
    static struct mc_pmp_svc_loc pmp_svc_loc_str;
    static struct kc_elig sql_kc_elig;

    static int    dte_current;
    static int    dte_current_minus_2_mnths;
    static int    dte_current_minus_3_mnths;
    static int    dte_current_minus_90;
    static int    dte_current_minus_180;
    static int    dte_curr_day;
    static int    dte_current_benefit_month;
    static int    dte_previous_benefit_month;
    static int    dte_choose_enroll;
    //static int    dte_kc_golive;
    static int    dte_enroll;
    static int    reinstatement_days = 0;
    static int    recip_assigned;
    static int    recip_assigned_medical;
    static char   recip_id_medicaid[12+1];
    static int    recip_dte_application = 0;
    static int    recip_dte_application_minus_3_mnths = 0;
    static int    recip_sak_case = 0;
    static char   recip_cde_county[2+1];
    static char   recip_zip_code[5+1];
    static char   recip_cde_sex[1+1];
    static int    recip_dte_cert = 0;
    static char   recip_region[1+1];
    static int    recip_dte_birth= -1;
    static int    recip_dte_death = -1;
    static char   recip_cde_pgm_health[12+1];
    static int    recp_enroll_days = 0;
    static int    pot_mother_age = 12;
    static int    clm_srch_months = 0;
    static int    recip_sak_cde_aid = 0;

    static int    sql_num_total_rcds = 0;

    static int    dte_kc19_enroll;
    static int    dte_kc_elig_verification;
    static int    dte_t21_max_retro;
    static int    sql_max_dte_morbidity;

    static int    sql_dte_enroll_pgm;
    static int    sql_sak_pub_hlth_pgm;
    static int    sql_sum_num_mc_actual;

    static int    sak_pub_hlth_kc19;
    static int    sak_pub_hlth_kc21;

    static int    sak_pmp_svc_kc19;
    static int    sak_pmp_svc_kc21;
    static int    sak_prov_kc19;
    static int    sak_prov_kc21;
    static int     autoassign_hck = mgd_FALSE;
    static int     autoassign_kc19 = mgd_FALSE;
    static int     autoassign_kc21 = mgd_FALSE;

    static int    sql_cnt_pls_assigns = 0;

    static int    sql_sak_prov = -1;
    static char    sql_prov_srv_loc[1+1] = " ";
    static int    sql_sak_pmp_svc_loc= -1;
    static int    sql_pcp_perf_prov = -1;
    static char    sql_pcp_perf_svc_loc[1+1] = " ";
    static int    sql_sak_pub_hlth = -1;
    static int    sql_case_pub_hlth = -1;
    static int    sql_case_sak_prov = -1;

    char sql_rpt_id_provider[9+1];
    char sql_rpt_cde_service_loc[1+1];
    char sql_rpt_cde_morbidity[1+1];
    int sql_rpt_num_count = 0;
    int sql_dte_kc_golive = 0;
    static int sql_tmp_parm = 0;
EXEC SQL END DECLARE SECTION;

/* Define the second connection to the database... */
EXEC SQL DECLARE DBCONN2 DATABASE;
EXEC SQL DECLARE DBCONN3 DATABASE;

/*********************************************************************/
/*                      Global Variables                             */
/*********************************************************************/
enum run_daily_monthly {
   MONTHLY,
   DAILY
} arg_run_level;


struct valid_pmp_info_type {
   char cde_enroll_status;
   char ind_panel_hold;
   char ind_autoassign;
   char ind_pmp_dist;
   double prov_dist;
} ;

static  char    *err_rpt_file;
static  FILE    *err_rpt_file_ptr;

static  char    *nb_rpt_file;
static  FILE    *nb_rpt_file_ptr;

static  char    *pcp_rpt_file;
static  FILE    *pcp_rpt_file_ptr;


static  int    cnt_potential_read  = 0;
static  int    cnt_elig            = 0;
static  int    cnt_not_elig        = 0;
static  int    cnt_kc19_assign   = 0;
static  int    cnt_kc21_assign      = 0;
static  int    cnt_bad_program     = 0;
static  int    cnt_pot_delete      = 0;
static  int    cnt_pot_update      = 0;

static  char    START_RSN_86_CASE_CONTINUITY[] = "86";            // KanCare Default - Case Continuity
static  int     case_logic_cnt = 0;

static  char    START_RSN_92_MORBIDITY[] = "92";             // KanCare Default - Morbidity
static  int     morbidity_cnt = 0;

static  char    START_RSN_93_90_DAY_RETRO_REATTACH[] = "93";
static  char    START_RSN_94_PREVIOUS_ASSIGNMENT[] = "94";

static  char    START_RSN_96_CONTINUITY_OF_PLAN[] = "96";             // Default - Continuity of Plan
static  int     pmp_chg_cnt = 0;

static  char    START_RSN_97_RETRO_ASSIGNMENT[] = "97";
static  char    START_RSN_A1_CHOICE_VIA_MEDICAID_APP[] = "A1";
static  char    START_RSN_08_NEWBORN[] = "08";

static  int     pmp_180_cnt = 0;
static  int     retro_future_pmp_cnt = 0;
static  int     mco_choice_cnt = 0;
static  int     prev_pmp_180_cnt = 0;

static int cnt_start_rsn_86 = 0; // -86 - KanCare Default - Case Continuity
static int cnt_start_rsn_92 = 0; // -92 - KanCare Default - Morbidity
static int cnt_start_rsn_93 = 0; // -93 - KanCare Default - 90 Day Retro-reattach
static int cnt_start_rsn_94 = 0; // -94 - KanCare Default - Previous Assignment
static int cnt_start_rsn_96 = 0; // -96 - Default - Continuity of Plan
static int cnt_start_rsn_97 = 0; // -97 - Retro assignment
static int cnt_start_rsn_A1 = 0; // A1 - Choice - Enrollment into KanCare via Medicaid App
static int cnt_start_rsn_undefined = 0;

static  int    SAK_ENT_AUTOASSIGN_INELIG = 0;
static  int    SAK_ENT_AUTOASSIGN_CASE_KC21 = 0;
static  int    SAK_ENT_AUTOASSIGN_CASE_KC19 = 0;
//static  int    SAK_ENT_AUTOASSIGN_HW_PCP_KC19 = 0;
//static  int    SAK_ENT_AUTOASSIGN_HW_PCP_KC21 = 0;
//static  int    SAK_ENT_AUTOASSIGN_LKN_PCP_KC19 = 0;
//static  int    SAK_ENT_AUTOASSIGN_LKN_PCP_KC21 = 0;
//static  int    SAK_ENT_AUTOASSIGN_NF_KC19 = 0;
//static  int    SAK_ENT_AUTOASSIGN_NF_KC21 = 0;
//static  int    SAK_ENT_AUTOASSIGN_HCBS_KC19 = 0;
//static  int    SAK_ENT_AUTOASSIGN_HCBS_KC21 = 0;
static  int    SAK_ENT_AUTOASSIGN_MORBIDITY_KC19 = 0;
static  int    SAK_ENT_AUTOASSIGN_MORBIDITY_KC21 = 0;
static  int    SAK_ENT_AUTOASSIGN_PREV_KC_PMP_PGM_CHG_KC19 = 0;
static  int    SAK_ENT_AUTOASSIGN_PREV_KC_PMP_PGM_CHG_KC21 = 0;
static  int    SAK_ENT_AUTOASSIGN_PREV_KC_PMP_180_DAY = 0;
static  int    SAK_ENT_AUTOASSIGN_CHOICE_KC19 = 0;
static  int    SAK_ENT_AUTOASSIGN_CHOICE_KC21 = 0;
static  int    SAK_ENT_AUTOASSIGN_NEWBORN_KC19 = 0;
static  int    SAK_ENT_AUTOASSIGN_NEWBORN_KC21 = 0;
static  int    SAK_ENT_AUTOASSIGN_RETRO_KC19 = 0;
static  int    SAK_ENT_AUTOASSIGN_RETRO_KC21 = 0;
static  int    SAK_ENT_AUTOASSIGN_90_DAY_RETRO_REATTACH_KC19 = 0;
static  int    SAK_ENT_AUTOASSIGN_90_DAY_RETRO_REATTACH_KC21 = 0;


static  int    mc_sak_elig_stop = 0;
static  int    sak_re_pmp_assign = -1;

static  char    curr_time[5];
static  int    ind_abend = mgd_FALSE;

/* Global Variable Flags */
static  int     messaging_on = mgd_TRUE;
static  int     arg_rsn_rpting = mgd_FALSE;
static  int     arg_save_to_db = mgd_FALSE;
static  int     recip_is_newborn = mgd_FALSE;
static  int     recip_is_presumptive = mgd_FALSE;

static  int     eligible_for_kc21 = mgd_FALSE;
static  int     eligible_for_kc19 = mgd_FALSE;
static  int     recip_mco_choice_count = mgd_FALSE;

static  char    time_stamp[64];
static  char    date_stamp[64];
static  char    time_stamp_initial[64];
static  char    date_stamp_initial[64];
static  time_t  process_start_time;
static  int     rcd_cnt = 0;
static  int     daily_retro_dte_effective = 0;
static  int     daily_retro_dte_end = 0;
static  int     after_month_end = mgd_FALSE;
static  int     UseKEES_Logic = 0;

/**************************************************************************/
/*                      SQL Cursor Declarations                           */
/**************************************************************************/
/* CURSOR NAME:  all_potentials                                           */
/* DESCRIPTION:  Retrieve all records from the potential table to verify  */
/*               and try to assign.                                       */
/**************************************************************************/
EXEC SQL AT DBCONN3 DECLARE all_potentials CURSOR for
SELECT  ptl.sak_recip,
        ptl.sak_pub_hlth,
        ptl.dte_added,
        ptl.cde_reason,
        ptl.cde_rsn_del,
        ptl.sak_pmp_transfer,
        ptl.dte_transfer_start,
        ptl.sak_mc_ent_add,
        NVL(remo.cde_morbidity, 0)
FROM    t_re_mc_recip     ptl,
        t_pub_hlth_pgm pgm,
        t_mc_re_morbidity remo,
        t_re_base base
WHERE   ptl.sak_pub_hlth = pgm.sak_pub_hlth
AND     pgm.cde_pgm_health in ('KC19', 'KC21')
AND     ptl.sak_recip = base.sak_recip
AND     ptl.sak_recip = remo.sak_recip(+)
AND     remo.dte_end(+) = 22991231  // Only retrieve the active morbidity ratings
ORDER BY NVL(remo.cde_morbidity, 0) desc, base.sak_case, base.sak_recip;

/**************************************************************************/
/* CURSOR NAME:  morbidity_rpt                                            */
/**************************************************************************/
EXEC SQL DECLARE morbidity_rpt CURSOR for
     SELECT prov.id_provider, pmp.cde_service_loc, pmpmo.cde_morbidity, pmpmo.num_count
       FROM t_mc_pmp_morbidity pmpmo,
            t_pmp_svc_loc pmp,
            t_pr_prov prov
      WHERE pmpmo.sak_pmp_ser_loc = pmp.sak_pmp_ser_loc
        AND pmp.sak_prov = prov.sak_prov
        AND pmpmo.dte_morbidity = :sql_max_dte_morbidity
   ORDER BY pmpmo.cde_morbidity, prov.id_provider, pmp.cde_service_loc;

/**************************************************************************/
/* CURSOR NAME:  kc_elig_cursor                                           */
/* DESCRIPTION:  Retrieve all records from the t_mc_kc_eligibility table  */
/*               for a recip/sak_pub_hlth.  A recipient will have one     */
/*               record per benefit month which will correspond to only   */
/*               one record on the potential table.                       */
/**************************************************************************/
EXEC SQL DECLARE kc_elig_cursor CURSOR for
 SELECT sak_short,
        dte_benefit_month
   FROM t_mc_kc_eligibility
  WHERE sak_recip = :sql_pntl_rcd.sak_recip
    AND sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
    AND TO_CHAR(dte_added, 'YYYYMMDDHH24MISS') > :sql_kc_elig_last_run_datetime
    AND sak_re_pmp_assign <= 0
ORDER BY dte_benefit_month;

/*********************************************************************/
/*                       Function Prototypes                         */
/*********************************************************************/
static void initialize(int, char *[]);
static void finalize(void);
static int ConnectToDB(void);
static int fetch_pot_info(void);
static int fetch_rpt_info(void);
static void display_rcd_cnt(void);
static void sqlerror(void);
static void get_assign_dates(void);
static void delete_pot_record(void);
static void ROLLBACK(void);
static void check_rc(struct mc_rtn_message *rtn_msg_str_ptr, int parm_rc, char *FNC);
static int det_pgm_assign_dte(int parm_sak_pub_hlth);
static int get_pot_sak_entity(int parm_sak_pub_hlth);
static int get_case_pmp(int *, int parm_dte_benefit_month);
static int get_next_sak(char parm_sak_name[18+1]);
static void update_pot_record(char parm_cde_reason[1+1]);
static void update_pot_rec_ltr_ind(char parm_ind_ltr[1+1], char parm_ind_rpt[1+1]);
static void get_recip_info(void);
static void update_pmp_panel_size(int parm_sak_pmp_ser_loc);
static void lock_recip_base(void);
static void process_cmd_line(int, char *[]);
static void print_usage(void);
static void save_to_db(char FNC[30]);
static void assign_case_pmp(void);
static void assign_prev_kc_pmp(void);
static int  check_pgm_elig(int , int );
static void display_pmp_info(int parm_sak_pmp_ser_loc, int parm_date);
static void assign_morbidity(void);
static int make_pmp_assgn(int parm_pmp_svc_loc, char *parm_assgn_rsn,
                          int parm_ent_autoassign, int parm_assgn_dte,
                          int parm_sak_pub_hlth, int parm_sak_prov_mbr,
                          char *parm_cde_svc_loc_mbr );
static void delete_kc_ptl_records(void);
static void display_final_counts(void);
static void mark_date(char* parm_date_stamp);
static void mark_time(char* parm_time_stamp);
static void count_total_rcds(void);
static int is_newborn(void);
static int assign_newborn(void);
static int  write_newborn_rec(void);
static void assign_prev_kc_pmp_180_day(void);
static int get_mco_choice_pmp(int *parm_sak_pub_hlth );
static void update_sys_parms(void);
static void assign_mco_choice(void);
static int fetch_kc_elig(void);
//static void assign_future_kc_pmp(void);
static int get_future_kc_pmp(int *parm_sak_pub_hlth );
static int get_prev_kc_pmp_180_day(int *parm_sak_pub_hlth );
static int get_prev_kc_pmp(int *parm_sak_pub_hlth );
static int get_morbidity_mco(int *parm_sak_pub_hlth );
static int is_presumptive(void);
static int get_mco_choice_count(void);
static int is_female_pw(int parm_dte);


/****************************************************************************/
/*                                                                          */
/* Function:    main()                                                      */
/*                                                                          */
/* Description: Controls the overall program logic.                         */
/*                                                                          */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/* 3/17/2016  15502 Rob Lohrke      Initialized common library variable     */
/*                                  that controls assignment end date.      */
/*                                                                          */
/****************************************************************************/
int main(int argc, char *argv[])
{
    char FNC[] = "main()";
       EXEC SQL BEGIN DECLARE SECTION;
          int sql_count = 0;
          int sql_dte_benefit_month_fom = 0;
          int sql_dte_benefit_month_lom = 0;
          int sql_recip_dte_application = 0;
          int sql_dte_current = 0;

       EXEC SQL END DECLARE SECTION;

    int cursor_rc = 0;
    int kc_elig_cursor_rc = 0;
    int rc_elig = 0;
    int rc_pmp_cnt = 0;
    int rc = 0;
    int daily_retro_bad_date = mgd_FALSE;
    int application_benefit_month_received = mgd_FALSE;
    int recip_ptl_add_date_used = mgd_FALSE;


    int sak_pmp_ser_loc;
    char tmp_type[10];

    int tmp_sak_pmp_ser_loc = 0;
    int tmp_mc_entity = 0;
    int tmp_sak_pub_hlth = 0;
    char tmp_start_rsn[2+1];
    char tmp_cde_pgm_health[5+1];

    /* Flags used to control logic flow */
    int messaging = mgd_TRUE;
    int incl_specified_pgm = mgd_TRUE;
    int lockin_recip = mgd_FALSE;

    int recip_is_currently_female_pw = mgd_FALSE;
    int recip_is_prev_female_pw = mgd_FALSE;
    int bene_prev_dte_benefit_month = 0;

    int assigned_in_last_3_months = mgd_FALSE;
    int assigned_prior_to_kc_elig_benefit_month = mgd_FALSE;



    /* Common Function structures */
    struct mc_is_reason rtn_reason_str;
    struct mc_rtn_message rtn_msg_str;
    struct mc_eligible rtn_lockin_str;

    initialize(argc, argv);

    sql_dte_current = dte_current;

    after_month_end = c_is_on_same_month(dte_kc_elig_verification, c_get_next_month_beg(dte_current));

    fprintf(err_rpt_file_ptr, "Current cycle date is [%d].  2 months prior to current cycle date is [%d],  3 months prior to current cycle date is [%d]\n",
        dte_current, dte_current_minus_2_mnths, dte_current_minus_3_mnths);

    if (after_month_end == mgd_TRUE)
        fprintf(err_rpt_file_ptr, "First of next month [%d] and latest KAECSES benefit month [%d] ARE in the same month\n - Processing AFTER month end has completed.\n",
            c_get_next_month_beg(dte_current), dte_kc_elig_verification);
    else
        fprintf(err_rpt_file_ptr, "First of next month [%d] and latest KAECSES benefit month [%d] are NOT in the same month\n - Processing BEFORE month end has completed.\n",
            c_get_next_month_beg(dte_current), dte_kc_elig_verification);

    EXEC SQL
     SELECT TO_CHAR((TO_DATE(:sql_dte_current, 'YYYYMMDD') - 90), 'YYYYMMDD'),
            TO_CHAR((TO_DATE(:sql_dte_current, 'YYYYMMDD') - 180), 'YYYYMMDD')
       INTO :dte_current_minus_90,
            :dte_current_minus_180
       FROM dual;

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: Could not retrieve current date minus 90 days from dual!\n", FNC);
        sqlerror();
        exit(FAILURE);
    }

    cursor_rc = fetch_pot_info();

    mc_debug_level = 1;

    while (cursor_rc == ANSI_SQL_OK)
    {
        bene_prev_dte_benefit_month = 0;
        rcd_cnt++;
        display_rcd_cnt();

        /* get the recip's ID, gender, age, etc... */
        get_recip_info();

        sql_dte_enroll_pgm = -1;

        /* Call det_pgm_assign_dte to set  recip_cde_pgm_health variable*/
        //det_pgm_assign_dte(sql_pntl_rcd.sak_pub_hlth);

        if (arg_rsn_rpting == mgd_TRUE)
        {
            fprintf(err_rpt_file_ptr, "\n\nProcessing BID [%s], sak_recip [%d], sak_case [%d], Pgm [%d], Added [%d], App Date [%d], cde_morbidity [%s]...\n",
                  recip_id_medicaid, sql_pntl_rcd.sak_recip, recip_sak_case, sql_pntl_rcd.sak_pub_hlth, sql_pntl_rcd.dte_added, recip_dte_application, sql_pntl_rcd.cde_morbidity);
            fflush(err_rpt_file_ptr);
        }

        if (sql_pntl_rcd.sak_pub_hlth == 80)
            strcpy(tmp_cde_pgm_health, "KC19");
        else
            strcpy(tmp_cde_pgm_health, "KC21");

        /* Initialize flags */
        recip_assigned = mgd_FALSE;
        recip_ptl_add_date_used = mgd_FALSE;
        recip_sak_cde_aid = 0;
        recip_is_newborn = is_newborn();
        recip_is_presumptive = is_presumptive();
        recip_mco_choice_count = get_mco_choice_count();
        c_set_asgn_dte_end_override(0);  // Defect 15502 - resetting the c_asgn_dte_end_override common library variable so that normal end date logic is utilized.

        if (arg_run_level == DAILY)
        {
            if (arg_rsn_rpting == mgd_TRUE)
                fprintf(err_rpt_file_ptr, "\tKanCare Daily Processing...\n");


            /****************************************************************************/
            /*                  Determine eligibility...                                */
            /****************************************************************************/
//            if (arg_rsn_rpting == mgd_TRUE)
//                fprintf(err_rpt_file_ptr, "\tDetermining eligibility for KC19...\n" );
//
            sql_dte_enroll_pgm = det_pgm_assign_dte(sak_pub_hlth_kc19);
//                        //rc_elig = check_pgm_elig(sak_pub_hlth_kc19, dte_kc_elig_verification);
//            //            rc_elig = check_pgm_elig(sak_pub_hlth_kc19, sql_dte_enroll_pgm);
//            //            if ( rc_elig == mgd_TRUE )
//            //                eligible_for_kc19 = mgd_TRUE;
//            //            else
//            //                eligible_for_kc19 = mgd_FALSE;
//            eligible_for_kc19 = mgd_TRUE;
//
//            /* if the bene is not eligible for the specified pgm, then remove the potential record... */
//            if ( (sql_pntl_rcd.sak_pub_hlth == sak_pub_hlth_kc19) && (eligible_for_kc19 == mgd_FALSE) )
//            {
//                cnt_not_elig++;
//                delete_pot_record();
//                recip_assigned = mgd_TRUE;
//            }


            /****************************************************************************/
            /*                  Newborn Logic.......                                    */
            /****************************************************************************/
            if ( recip_assigned == mgd_FALSE &&
                 recip_is_newborn == mgd_TRUE )

            {
                if (arg_rsn_rpting == mgd_TRUE)
                    fprintf(err_rpt_file_ptr, "\tDaily Newborn Processing...\n");
                assign_newborn();

            }

            if (recip_assigned == mgd_FALSE)
            {
                if (arg_rsn_rpting == mgd_TRUE)
                    fprintf(err_rpt_file_ptr, "\tDaily Retro Processing...\n");

                sql_recip_dte_application = c_get_month_begin(recip_dte_application);

                // Determine if we received the application benefit month was received in the most recent KAECSES run...
                EXEC SQL
                 SELECT COUNT(*)
                   INTO :sql_count
                   FROM t_mc_kc_eligibility
                  WHERE sak_recip = :sql_pntl_rcd.sak_recip
                    AND sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
                    AND TO_CHAR(dte_added, 'YYYYMMDDHH24MISS') > :sql_kc_elig_last_run_datetime
                    AND dte_benefit_month = :sql_recip_dte_application;

                if (sqlca.sqlcode != ANSI_SQL_OK)
                {
                    fprintf (stderr, "%s - ERROR: Could not count t_mc_kc_eligibility records for sak_recip [%d]!\n", FNC, sql_pntl_rcd.sak_recip);
                    sqlerror();
                    exit(FAILURE);
                }

                if (sql_count > 0)
                {
                    fprintf(err_rpt_file_ptr, "\t\tKC Eligibility record was sent for application month.\n");
                    application_benefit_month_received = mgd_TRUE;
                }
                else
                {
                    fprintf(err_rpt_file_ptr, "\t\tKC Eligibility record was NOT sent for application month.\n");
                    application_benefit_month_received = mgd_FALSE;
                }

                EXEC SQL
                 SELECT count(*)
                   INTO :sql_count
                   FROM T_RE_ELIG elig
                  WHERE elig.sak_recip = :sql_pntl_rcd.sak_recip
                    AND elig.sak_pub_hlth IN (SELECT sak_pub_hlth
                                                FROM t_pub_hlth_pgm
                                               WHERE cde_pgm_health IN ('KC19', 'KC21'))
                    AND elig.dte_end >= :dte_current_minus_3_mnths
                    AND elig.dte_end < :dte_kc_elig_verification
                    AND elig.cde_status1 <> 'H';

                if (sqlca.sqlcode != ANSI_SQL_OK)
                {
                    fprintf (stderr, "%s - ERROR: Could not count KanCare t_re_elig records for sak_recip [%d] within 90 days!\n", FNC, sql_pntl_rcd.sak_recip);
                    sqlerror();
                    exit(FAILURE);
                }

                if (sql_count > 0)
                {
                    fprintf(err_rpt_file_ptr, "\t\tBeneficiary has non-historied KC assignment in last 3 months (end date was prior to [%d] and on/after [%d]).\n",
                        dte_kc_elig_verification, dte_current_minus_3_mnths);
                    assigned_in_last_3_months = mgd_TRUE;
                }
                else
                {
                    fprintf(err_rpt_file_ptr, "\t\tBeneficiary does NOT have KC assignment that ended before [%d] and ended on/after [%d] (in last 3 months).\n",
                        dte_kc_elig_verification, dte_current_minus_3_mnths);
                    assigned_in_last_3_months = mgd_FALSE;
                }

                // Determine if the beneficiary has a PW aid category and is a female...
                if (after_month_end == mgd_TRUE)
                {
                    recip_is_currently_female_pw = is_female_pw(dte_previous_benefit_month);
                }
                else
                {
                    recip_is_currently_female_pw = is_female_pw(dte_kc_elig_verification);
                }

                EXEC SQL open kc_elig_cursor;

                if (sqlca.sqlcode != ANSI_SQL_OK)
                {
                    fprintf (stderr, "   ERROR: could not open the kc_elig_cursor Cursor for sak_recip [%d], sak_pub_hlth [%d]!\n", sql_pntl_rcd.sak_recip, sql_pntl_rcd.sak_pub_hlth);
                    sqlerror();
                    exit(FAILURE);
                }

                kc_elig_cursor_rc = fetch_kc_elig();

                if (kc_elig_cursor_rc == ANSI_SQL_NOTFOUND)
                    fprintf(err_rpt_file_ptr, "\t\tNo daily KC eligibility records found.\n");

                tmp_mc_entity = 0;
                strcpy(tmp_start_rsn, "  ");

                while (kc_elig_cursor_rc == ANSI_SQL_OK)
                {
                    // Reset flags...
                    recip_assigned = mgd_FALSE;
                    daily_retro_bad_date = mgd_FALSE;
                    recip_is_prev_female_pw = mgd_FALSE;
                    assigned_prior_to_kc_elig_benefit_month = mgd_FALSE;

                    fprintf(err_rpt_file_ptr, "\n\t\tRetro Benefit Month [%d] Found\n", sql_kc_elig.dte_benefit_month);

                    if (sql_kc_elig.dte_benefit_month < sql_dte_kc_golive)
                    {
                        fprintf(err_rpt_file_ptr, "\t\t\tRetro benefit month is prior to KanCare golive [%d] - proceeding to next benefit month.\n", sql_dte_kc_golive);
                        daily_retro_bad_date = mgd_TRUE;
                    }
                    else
                    {
                        // Now see if the beneficiary had an assignment that was effective prior to kc elig benefit month (within last 3 months)
                        // AND was effective prior to the kc elig benefit month...
                        EXEC SQL
                         SELECT count(*)
                           INTO :sql_count
                           FROM T_RE_ELIG elig
                          WHERE elig.sak_recip = :sql_pntl_rcd.sak_recip
                            AND elig.sak_pub_hlth IN (SELECT sak_pub_hlth
                                                        FROM t_pub_hlth_pgm
                                                       WHERE cde_pgm_health IN ('KC19', 'KC21'))
                            AND elig.dte_end >= :dte_current_minus_3_mnths
                            AND elig.dte_end < :dte_kc_elig_verification
                            AND elig.dte_effective < :sql_kc_elig.dte_benefit_month  // <-- this is the real difference between the above query and this one
                            AND elig.cde_status1 <> 'H';

                        if (sqlca.sqlcode != ANSI_SQL_OK)
                        {
                            fprintf (stderr, "%s - ERROR: Could not count KanCare t_re_elig records for sak_recip [%d] within 90 days #2!\n", FNC, sql_pntl_rcd.sak_recip);
                            sqlerror();
                            exit(FAILURE);
                        }

                        if (sql_count > 0)
                        {
                            fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary has non-historied KC assignment in last 3 months \n"
                                "\t\t\t\t-   (end date was prior to [%d] and on/after [%d], effective date was prior to [%d]).\n",
                                dte_kc_elig_verification, dte_current_minus_3_mnths, sql_kc_elig.dte_benefit_month);
                            assigned_prior_to_kc_elig_benefit_month = mgd_TRUE;
                        }
                        else
                        {
                            fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary does NOT have KC assignment that ended before [%d] and \n"
                                "\t\t\t\t-   ended on/after [%d], and effective prior to [%d] (in last 3 months).\n",
                                dte_kc_elig_verification, dte_current_minus_3_mnths, sql_kc_elig.dte_benefit_month);
                            assigned_prior_to_kc_elig_benefit_month = mgd_FALSE;
                        }

                        // KC19 Logic...
                        if (sql_pntl_rcd.sak_pub_hlth == 80)
                        {
                            fprintf(err_rpt_file_ptr, "\t\t\tKC19 - Executing KC19-specific retro benefit month logic...\n");

                            if (recip_is_currently_female_pw == mgd_TRUE)
                            {
                                // Determine if the beneficiary has a PW aid category and is a female for the specified benefit month...
                                recip_is_prev_female_pw = is_female_pw(sql_kc_elig.dte_benefit_month);
                                if (recip_is_prev_female_pw == mgd_TRUE)
                                    fprintf(err_rpt_file_ptr, "\t\t\tKC19 Beneficiary is female PW for retro benefit month.\n");
                                else
                                    fprintf(err_rpt_file_ptr, "\t\t\tKC19 Beneficiary is NOT female PW for retro benefit month.\n");
                            }

                            // If the 3 month assignment wasn't effective prior to the kc elig benefit month, then I can't use the following rule, and I need to fall down further...
                            if ( (assigned_in_last_3_months == mgd_TRUE) &&
                                 (sql_kc_elig.dte_benefit_month >= dte_current_minus_3_mnths) &&
                                 (assigned_prior_to_kc_elig_benefit_month == mgd_TRUE) )
                            {
                                // RULE:  Beneficiary has KC assignment in last 3 months.  If there is an assignment within the last three months we should
                                //        be processing the eligibility record and assignment.  The prior medical rule is not applicable and the
                                //        non-assignable retro rule is not applicable.

                                // Verify that the eligibility month is not more than 3 months in the past, if it is, then proceed to the next month...
                                fprintf(err_rpt_file_ptr, "\t\t\tKC19 Retro benefit month for beneficiary is after KanCare golive [%d],\n"
                                                          "\t\t\t-   after 3 months in the past [%d], and beneficiary has an assignment in the last 3 months - continuing.\n",
                                    sql_dte_kc_golive, dte_current_minus_3_mnths);

                            }
                            else if ( (recip_is_prev_female_pw == mgd_TRUE) && (sql_kc_elig.dte_benefit_month >= dte_current_minus_3_mnths) )
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tKC19 Beneficiary is female PW and retro benefit month is less than 3 months in the past - continuing.\n");
                            }
                            else if ( (recip_is_newborn == mgd_TRUE) && (sql_kc_elig.dte_benefit_month >= dte_current_minus_3_mnths) )
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tKC19 Beneficiary is newborn and benefit month is less than 3 months in the past - continuing.\n");
                            }
                            else
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tKC19 Either beneficiary did not have 3 month assignment,\n"
                                                          "\t\t\t-   was not a female PW, was not a newborn, eligibility month\n"
                                                          "\t\t\t-   did not have an assignment in last 90 days that was effective prior to kc benefit month, or\n"
                                                          "\t\t\t-   was more than 3 months in the past [%d] - proceeding with other retro logic.\n",
                                    dte_current_minus_3_mnths);

                                if (sql_kc_elig.dte_benefit_month < recip_dte_application_minus_3_mnths)
                                {
                                    fprintf(err_rpt_file_ptr, "\t\t\tKC19 Retro benefit month is prior to application date minus 90 days [%d] - proceeding to next benefit month.\n", recip_dte_application_minus_3_mnths);
                                    daily_retro_bad_date = mgd_TRUE;
                                }
                                else
                                {
                                    fprintf(err_rpt_file_ptr, "\t\t\tKC19 Retro benefit month is after KanCare golive [%d] and after application date minus 90 days [%d] - continuing.\n",
                                        sql_dte_kc_golive, recip_dte_application_minus_3_mnths);

                                    if ( (application_benefit_month_received == mgd_TRUE) || (assigned_in_last_3_months == mgd_FALSE) )
                                    {
                                        fprintf(err_rpt_file_ptr, "\t\t\tKC19 Retro benefit month for application month received OR was not assigned in last 3 months - utilizing prior med logic.\n");

                                        if (c_get_month_end(recip_dte_application) >= dte_current_minus_2_mnths)
                                        {
                                            // If beneficiary's eligibility month is within 90 days prior to the application month and the
                                            // application date is within 60 days prior to the date received by MMIS (cycle date = dte_current, dte_current_minus_2_mnths), but after the KanCare go-live,
                                            // then create assignment for benefit months.
                                            fprintf(err_rpt_file_ptr, "\t\t\tKC19 PRIOR MED: Last of month of Application date [%d] is within cycle date minus 2 months [%d] - continuing.\n",
                                                c_get_month_end(recip_dte_application), dte_current_minus_2_mnths);
                                            //daily_retro_bad_date = mgd_FALSE;
                                        }
                                        else
                                        {
                                            // If beneficiary's eligibility month is within 90 days prior to the application month and the application
                                            // date is NOT within 60 days prior to the date received by MMIS (cycle date = dte_current, dte_current_minus_2_mnths), but after KanCare go-live, create
                                            // assignment for *current* benefit month.

                                            if (sql_kc_elig.dte_benefit_month == dte_current_benefit_month)
                                            {
                                                fprintf(err_rpt_file_ptr, "\t\t\tKC19 PRIOR MED: Last of month of Application date [%d] is NOT within cycle date minus 2 months [%d] and benefit month "
                                                    "\n\t\t\t*    is for current benefit month [%d] - continuing.\n",
                                                    c_get_month_end(recip_dte_application), dte_current_minus_2_mnths, dte_current_benefit_month);
                                                //daily_retro_bad_date = mgd_FALSE;
                                            }
                                            else
                                            {
                                                // RULE:  "assignment is OK if the specified benefit month is the previous KAECSES benefit month and we are running after month end"

                                                // after_month_end = c_is_on_same_month(dte_kc_elig_verification, c_get_next_month_beg(dte_current));   dte_previous_benefit_month

                                                if ( (sql_kc_elig.dte_benefit_month == dte_previous_benefit_month) && (after_month_end == mgd_TRUE) )
                                                {
                                                    fprintf(err_rpt_file_ptr, "\t\t\tKC19 PRIOR MED: Last of month of Application date [%d] is NOT within cycle date minus 60 days [%d] and listed "
                                                        "\n\t\t\t*    benefit month is for previous benefit month [%d] and processing after month end - continuing.\n",
                                                        c_get_month_end(recip_dte_application), dte_current_minus_2_mnths, dte_previous_benefit_month);
                                                }
                                                else
                                                {
                                                    fprintf(err_rpt_file_ptr, "\t\t\tKC19 PRIOR MED: Last of month of Application date [%d] is NOT within cycle date minus 60 days [%d] and specified "
                                                        "\n\t\t\t*    benefit month is NOT for current benefit month [%d] - proceeding to next benefit month.\n",
                                                        c_get_month_end(recip_dte_application), dte_current_minus_2_mnths, dte_current_benefit_month);
                                                    daily_retro_bad_date = mgd_TRUE;
                                                }
                                            }
                                        }
                                    }
                                    else
                                    {
                                        fprintf(err_rpt_file_ptr, "\t\t\tKC19 Retro benefit month for application month NOT received - utilizing Non-Assignable Retro Eligibility logic.\n");

                                        // Application date within 60 days of current date,
                                        // and benefit month is greater than application benefit month,
                                        // and application benefit month was not received and
                                        // and application benefit month less than current benefit month,
                                        // and beneficiary has been assigned within the last 90 days
                                        // then assign for all retro months,
                                        // otherwise assign for current benefit month only (and previous benefit month if after month end).
                                        if ( (c_get_month_end(recip_dte_application) >= dte_current_minus_2_mnths) &&
                                             (sql_kc_elig.dte_benefit_month > (c_get_month_begin(recip_dte_application))) &&
                                             (application_benefit_month_received == mgd_FALSE) &&
                                             (sql_recip_dte_application < dte_kc_elig_verification) &&
                                             (assigned_in_last_3_months == mgd_TRUE) )
                                        {
                                            // assign for all retro months,
                                            fprintf(err_rpt_file_ptr, "\t\t\tKC19 NARE:  Application date within 60 days of current date &\n"
                                                                      "\t\t\t\tbenefit month is greater than application benefit month &\n"
                                                                      "\t\t\t\tapplication benefit month was not received &\n"
                                                                      "\t\t\t\tapplication benefit month less than current benefit month &\n"
                                                                      "\t\t\t\tbeneficiary has been assigned within the last 90 days - continuing.\n");
                                        }
                                        else
                                        {
                                            // otherwise assign for current benefit month only (and previous benefit month if after month end).
                                            if (sql_kc_elig.dte_benefit_month == dte_current_benefit_month)
                                            {
                                                fprintf(err_rpt_file_ptr, "\t\t\tKC19 NARE:  Benefit month is for current benefit month [%d] - continuing.\n", dte_current_benefit_month);
                                            }
                                            else
                                            {
                                                // RULE:  "assignment is OK if the specified benefit month is the previous KAECSES benefit month and we are running after month end"
                                                if ( (sql_kc_elig.dte_benefit_month == dte_previous_benefit_month) && (after_month_end == mgd_TRUE) )
                                                {
                                                    fprintf(err_rpt_file_ptr, "\t\t\tKC19 NARE:  Benefit month is for previous benefit month [%d] and processing after month end - continuing.\n",
                                                        dte_previous_benefit_month);
                                                }
                                                else
                                                {
                                                    fprintf(err_rpt_file_ptr, "\t\t\tKC19 NARE:  Benefit month is NOT for current benefit month [%d] - proceeding to next benefit month.\n",
                                                        dte_current_benefit_month);
                                                    daily_retro_bad_date = mgd_TRUE;
                                                    
                                                    //If presumptive and the potential add date month is in the same month as the current record, then need to flag
                                                    //the potential date so it won't be used.
                                                    if (UseKEES_Logic == 1 && recip_is_presumptive == mgd_TRUE && (sql_pntl_rcd.dte_added/100 == sql_kc_elig.dte_benefit_month/100) )
                                                    {
                                                        recip_ptl_add_date_used = mgd_TRUE;
                                                        fprintf(err_rpt_file_ptr, "\t\t\tKC19 NARE:  Cancelling the Potential_Add date[%d] so it can't be used to make an assignment.\n",
                                                                sql_pntl_rcd.dte_added);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else // KC21 Logic...
                        {
                            fprintf(err_rpt_file_ptr, "\t\t\tKC21 - Executing KC21-specific retro benefit month logic...\n");
                            if (sql_kc_elig.dte_benefit_month < dte_t21_max_retro)
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tKC21 Retro benefit month is prior to max retro month for T21 [%d] - proceeding to next benefit month.\n", dte_t21_max_retro);
                                daily_retro_bad_date = mgd_TRUE;
                            }
                            else
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tKC21 Retro benefit month is after max retro month for T21 [%d] and after KanCare golive [%d] - continuing.\n",
                                    dte_t21_max_retro, sql_dte_kc_golive);
                                //daily_retro_bad_date = mgd_FALSE;
                            }
                        }
                    }

                    if ( (daily_retro_bad_date == mgd_FALSE) && (assigned_in_last_3_months == mgd_FALSE) )
                    {
                        if (recip_is_presumptive == mgd_TRUE)
                        {
                            if (sql_kc_elig.dte_benefit_month < c_get_month_begin(sql_pntl_rcd.dte_added))
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tRetro benefit month is prior to first of month of proration date for presumptive [%d] - proceeding to next benefit month.\n",
                                    sql_pntl_rcd.dte_added);
                                daily_retro_bad_date = mgd_TRUE;
                            }
                            else
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tRetro benefit month is NOT prior to first of month of proration date for presumptive [%d] - continuing.\n",
                                    sql_pntl_rcd.dte_added);
                            }
                        }
                    }

                    if (daily_retro_bad_date == mgd_FALSE)
                    {
                        // Recheck the eligibility for LOC - since the LOC is processed after the main eligibility, it's possible that the beneficiary is not longer eligible for KanCare...
                        if (sql_pntl_rcd.sak_pub_hlth == 80)
                        {
                            rc = c_is_valid_mc_liv_arng(&rtn_lockin_str, &rtn_msg_str, sql_pntl_rcd.sak_recip, sql_pntl_rcd.sak_pub_hlth, sql_kc_elig.dte_benefit_month, mgd_TRUE);

                            if (rc == mgd_FALSE)
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tKC19 Retro benefit month has invalid living condition - proceeding to next benefit month.\n");
                                daily_retro_bad_date = mgd_TRUE;
                            }
                            else
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tKC19 Retro benefit month has valid living condition - continuing.\n");
                            }
                        }
                    }

                    sql_dte_benefit_month_fom = sql_kc_elig.dte_benefit_month;
                    sql_dte_benefit_month_lom = c_get_month_end(sql_kc_elig.dte_benefit_month);

                    if (daily_retro_bad_date == mgd_FALSE)
                    {
                        EXEC SQL
                         SELECT count(*)
                           INTO :sql_count
                           FROM T_RE_ELIG elig
                          WHERE elig.sak_recip = :sql_pntl_rcd.sak_recip AND
                                elig.sak_pub_hlth IN (SELECT sak_pub_hlth
                                                        FROM t_pub_hlth_pgm
                                                       WHERE cde_pgm_health IN ('KC19', 'KC21')) AND
                                elig.dte_effective <= :sql_dte_benefit_month_lom AND
                                elig.dte_end >= :sql_dte_benefit_month_fom AND
                                elig.cde_status1 <> 'H';

                        if (sqlca.sqlcode != ANSI_SQL_OK)
                        {
                            fprintf (stderr, "%s - ERROR: Could not count KanCare t_re_elig records for sak_recip [%d]!\n", FNC, sql_pntl_rcd.sak_recip);
                            sqlerror();
                            exit(FAILURE);
                        }

                        if (sql_count > 0)
                        {
                            fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary already has KC assignment that covers the benefit month - proceeding to next benefit month.\n");
                            daily_retro_bad_date = mgd_TRUE;
                        }
                        else
                        {
                            fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary does NOT already have KC assignment that covers the benefit month - continuing.\n");
                        }
                    }


                    // ********************************************************************************************************************************
                    // ********************************************************************************************************************************
                    // If a PMP was found, but an assignment not yet created (because there was just one eligibility month), then make one.
                    // NOTE:  The logic within the following 'if' statement MUST be identical to the code below marked DAILY_CODE_LOOP
                    // 1_DAILY_CODE_LOOP_BEGIN
                    // ********************************************************************************************************************************
                    // ********************************************************************************************************************************
                    if (tmp_sak_pmp_ser_loc > 0)
                    {
                        if (sql_kc_elig.dte_benefit_month == c_get_next_month_beg(bene_prev_dte_benefit_month))
                        {
                            daily_retro_dte_end = c_get_month_end(sql_kc_elig.dte_benefit_month);
                        }
                        else
                        {
                            if (daily_retro_dte_end != c_get_month_end(dte_kc_elig_verification))
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tLast eligibility month [%d] is NOT the end of the latest KAECSES month [%d] - setting end date to [%d]\n",
                                    daily_retro_dte_end, c_get_month_end(dte_kc_elig_verification), daily_retro_dte_end);
                                // Override the end date logic that's in c_do_create_pmp_assign_2...
                                c_set_asgn_dte_end_override(daily_retro_dte_end);
                            }
                            else
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tLast eligibility month [%d] IS the end of the latest KAECSES month [%d] - allowing EOT end date.\n",
                                    daily_retro_dte_end, c_get_month_end(dte_kc_elig_verification));
                                //strcpy(tmp_start_rsn, "99");
                            }

                            if (recip_dte_death > 0)
                            {
                                if (   (c_get_asgn_dte_end_override() > recip_dte_death) ||
                                     ( (c_get_asgn_dte_end_override() == 0) && (recip_dte_death > 0) ) )
                                {
                                    fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary has dte_death on file [%d] - resetting end date [%d] to death date.\n", recip_dte_death, c_get_asgn_dte_end_override());
                                    c_set_asgn_dte_end_override(recip_dte_death);
                                }
                                else
                                {
                                    fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary has dte_death on file [%d] - reset of end date [%d] to death date UNNECESSARY.\n", recip_dte_death, c_get_asgn_dte_end_override());
                                }
                            }

                            if (sql_pntl_rcd.sak_pub_hlth == 81)
                            {
                                /*  Note, the logic below evolved to the point of what is shown here; however,
                                    the existing code was left in case we need to revert back.
                                        if (recip_ptl_add_date_used == mgd_FALSE)
                                        {
                                            daily_retro_dte_effective = sql_pntl_rcd.dte_added;
                                            recip_ptl_add_date_used = mgd_TRUE;
                                        }
                                */
                                if ( (daily_retro_dte_effective == dte_kc_elig_verification) && (recip_ptl_add_date_used == mgd_FALSE) )
                                {
                                    daily_retro_dte_effective = sql_pntl_rcd.dte_added;
                                    recip_ptl_add_date_used = mgd_TRUE;
                                    fprintf(err_rpt_file_ptr, "\t\t\tKC21 Beneficiary benefit month is for current benefit month [%d] - set dte_effective to pntl add date [%d].\n",
                                        dte_kc_elig_verification, daily_retro_dte_effective);
                                }
                                else
                                {
                                    if ( (c_get_next_month_beg(daily_retro_dte_effective) == dte_kc_elig_verification) &&
                                         (after_month_end == mgd_TRUE) &&
                                         (recip_ptl_add_date_used == mgd_FALSE) )
                                    {
                                        daily_retro_dte_effective = sql_pntl_rcd.dte_added;
                                        recip_ptl_add_date_used = mgd_TRUE;
                                        fprintf(err_rpt_file_ptr, "\t\t\tKC21 Beneficiary benefit month is for next benefit month [%d] and after month end  - set dte_effective to pntl add date [%d].\n",
                                            dte_kc_elig_verification, daily_retro_dte_effective);
                                    }
                                    else
                                    {
                                        if (UseKEES_Logic == 1 )
                                        {
                                            if (recip_ptl_add_date_used == mgd_FALSE)
                                            {
                                                daily_retro_dte_effective = sql_pntl_rcd.dte_added;
                                                recip_ptl_add_date_used = mgd_TRUE;
                                                fprintf(err_rpt_file_ptr, "\t\t\tKC21 KEES day specific retro - set dte_effective to pntl add date [%d].\n", 
                                                    daily_retro_dte_effective);
                                            }
                                            else
                                            {
                                                fprintf(err_rpt_file_ptr, "\t\t\tKC21 pntl add date already used [%d] - utilizing benefit month [%d] for asgn effective date.\n",
                                                    sql_pntl_rcd.dte_added, dte_kc_elig_verification);
                                            }
                                        }
                                        else
                                        {
                                            fprintf(err_rpt_file_ptr, "\t\t\tKC21 Beneficiary benefit month is NOT for current benefit month [%d] - utilizing benefit month [%d] for asgn effective date.\n",
                                                dte_kc_elig_verification, daily_retro_dte_effective);
                                        }
                                    }
                                }
                            }

                            // For presumptive eligibles, we will always use the potential date for the effective date...
                            if (recip_is_presumptive == mgd_TRUE)
                            {
                                if (sql_pntl_rcd.dte_added >= sql_dte_kc_golive)
                                {
                                    if (recip_ptl_add_date_used == mgd_FALSE)
                                    {
                                        recip_ptl_add_date_used = mgd_TRUE;
                                        fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary is presumptive and dte_added >= KC golive.  Changing effective date from [%d] to [%d]\n", daily_retro_dte_effective, sql_pntl_rcd.dte_added);
                                        daily_retro_dte_effective = sql_pntl_rcd.dte_added;
                                    }
                                    else
                                    {
                                        fprintf(err_rpt_file_ptr, "\t\t\tPresumptive pntl add date already used [%d] - utilizing benefit month [%d] for asgn effective date.\n", sql_pntl_rcd.dte_added, dte_kc_elig_verification);
                                    }
                                }
                                else
                                {
                                    fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary is presumptive and dte_added < KC golive.  Changing effective date from [%d] to [%d]\n", daily_retro_dte_effective, sql_dte_kc_golive);
                                    daily_retro_dte_effective = sql_dte_kc_golive;
                                }
                            }
                            else
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary is NOT presumptive.  Leaving effective date as [%d]\n", daily_retro_dte_effective);
                            }

                            if ((recip_dte_death > 0) && (recip_dte_death <= daily_retro_dte_effective))
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary DOD [%d] is prior to retro effective date [%d] - unable to create assignment.\n",
                                    recip_dte_death, daily_retro_dte_effective);
                            }
                            else
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary DOD [%d] is NOT prior to retro effective date [%d] - creating assignment.\n",
                                    recip_dte_death, daily_retro_dte_effective);
                                make_pmp_assgn(tmp_sak_pmp_ser_loc, tmp_start_rsn,
                                              tmp_mc_entity,
                                              daily_retro_dte_effective, sql_pntl_rcd.sak_pub_hlth, 0, " ");
                            }

                            tmp_sak_pmp_ser_loc = 0;
                            recip_assigned = mgd_FALSE;
                        }
                    }
                    // ********************************************************************************************************************************
                    // ********************************************************************************************************************************
                    // 1_DAILY_CODE_LOOP_END
                    // ********************************************************************************************************************************
                    // ********************************************************************************************************************************


                    /****************************************************************************/
                    /*                  Future KC Enrollment Logic.......                       */
                    /****************************************************************************/
                    //if (assigned_in_last_3_months == mgd_FALSE)
                    if (assigned_prior_to_kc_elig_benefit_month == mgd_FALSE)  // 20130425_mod
                    {
                        if ( (recip_assigned == mgd_FALSE) && (daily_retro_bad_date == mgd_FALSE) && (tmp_sak_pmp_ser_loc <= 0) )
                        {
                            fprintf(err_rpt_file_ptr, "\t\t\tDaily Future Assignment Processing...\n");

                            tmp_sak_pmp_ser_loc = get_future_kc_pmp(&tmp_sak_pub_hlth);

                            if ( tmp_sak_pmp_ser_loc > 0 )
                            {
                                daily_retro_dte_effective = sql_kc_elig.dte_benefit_month;
                                daily_retro_dte_end = c_get_month_end(sql_kc_elig.dte_benefit_month);
                                strcpy(tmp_start_rsn, START_RSN_97_RETRO_ASSIGNMENT);  //  Retro assignment
                                tmp_mc_entity = (tmp_sak_pub_hlth == sak_pub_hlth_kc21) ? SAK_ENT_AUTOASSIGN_RETRO_KC21 : SAK_ENT_AUTOASSIGN_RETRO_KC19;
                                retro_future_pmp_cnt++;
                            }
                        }
                    }
                    else
                    {
                        fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary was assigned during previous 3 months - bypassing Daily Future Assignment Processing...\n");
                    }

                    /****************************************************************************/
                    /*                     MCO Choice Logic...                                  */
                    /****************************************************************************/
                    //if (assigned_in_last_3_months == mgd_FALSE)
                    if (assigned_prior_to_kc_elig_benefit_month == mgd_FALSE)  // 20130425_mod
                    {
                        if ( (recip_assigned == mgd_FALSE) && (daily_retro_bad_date == mgd_FALSE) && (tmp_sak_pmp_ser_loc <= 0) )
                        {
                            fprintf(err_rpt_file_ptr, "\t\t\tDaily MCO Choice Processing...\n");

                            tmp_sak_pmp_ser_loc = get_mco_choice_pmp(&tmp_sak_pub_hlth);

                            if ( tmp_sak_pmp_ser_loc > 0 )
                            {
                                daily_retro_dte_effective = sql_kc_elig.dte_benefit_month;
                                daily_retro_dte_end = c_get_month_end(sql_kc_elig.dte_benefit_month);
                                strcpy(tmp_start_rsn, START_RSN_A1_CHOICE_VIA_MEDICAID_APP);
                                tmp_mc_entity = (tmp_sak_pub_hlth == sak_pub_hlth_kc21) ? SAK_ENT_AUTOASSIGN_CHOICE_KC21 : SAK_ENT_AUTOASSIGN_CHOICE_KC19;
                                mco_choice_cnt++;
                            }
                        }
                    }
                    else
                    {
                        fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary was assigned during previous 3 months - bypassing Daily MCO Choice Processing.\n");
                    }

                    /****************************************************************************/
                    /*                     Previous KC Assignment Logic for KC19...             */
                    /****************************************************************************/
                    // See if the beneficiary has a previous KC assignment that was Historied due
                    // to the beneficiary losing eligibility.  If that happened because the beneficiary
                    // went from TXXI to TXIX, then we should try to assign them to the same MCO that
                    // they previously had.
                    //if (assigned_in_last_3_months == mgd_FALSE)
                    if (assigned_prior_to_kc_elig_benefit_month == mgd_FALSE)  // 20130425_mod
                    {
                        if ( (recip_assigned == mgd_FALSE) && (daily_retro_bad_date == mgd_FALSE) && (tmp_sak_pmp_ser_loc <= 0) )
                        {
                            if (arg_rsn_rpting == mgd_TRUE)
                                fprintf(err_rpt_file_ptr, "\t\t\tDaily Previous KC PMP (historied) Processing...\n");

                            tmp_sak_pmp_ser_loc = get_prev_kc_pmp(&tmp_sak_pub_hlth);

                            if ( tmp_sak_pmp_ser_loc > 0 )
                            {
                                daily_retro_dte_effective = sql_kc_elig.dte_benefit_month;
                                daily_retro_dte_end = c_get_month_end(sql_kc_elig.dte_benefit_month);
                                strcpy(tmp_start_rsn, START_RSN_96_CONTINUITY_OF_PLAN);
                                tmp_mc_entity = (tmp_sak_pub_hlth == sak_pub_hlth_kc21) ? SAK_ENT_AUTOASSIGN_PREV_KC_PMP_PGM_CHG_KC21 : SAK_ENT_AUTOASSIGN_PREV_KC_PMP_PGM_CHG_KC19;
                                pmp_chg_cnt++;
                            }
                        }
                    }
                    else
                    {
                        fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary was assigned during previous 3 months - bypassing Daily Previous KC PMP (historied) Processing.\n");
                    }

                    /****************************************************************************/
                    /*                      Previous PMP 180 Logic for KC19...                  */
                    /****************************************************************************/
                    if ( (recip_assigned == mgd_FALSE) && (daily_retro_bad_date == mgd_FALSE) && (tmp_sak_pmp_ser_loc <= 0) )
                    {
                        fprintf(err_rpt_file_ptr, "\t\t\tDaily Previous PMP (180) Processing...\n");

                        tmp_sak_pmp_ser_loc = get_prev_kc_pmp_180_day(&tmp_sak_pub_hlth);

                        if ( tmp_sak_pmp_ser_loc > 0 )
                        {
                            daily_retro_dte_effective = sql_kc_elig.dte_benefit_month;
                            daily_retro_dte_end = c_get_month_end(sql_kc_elig.dte_benefit_month);

                            // See if the previous assignment for the beneficiary was ended due to elig loss and ended the day prior to the current benefit month...
                            EXEC SQL
                             SELECT count(*)
                               INTO :sql_count
                               FROM T_RE_ELIG elig, t_re_pmp_assign asgn
                              WHERE elig.sak_recip = :sql_pntl_rcd.sak_recip
                                AND elig.sak_pub_hlth IN (SELECT sak_pub_hlth
                                                            FROM t_pub_hlth_pgm
                                                           WHERE cde_pgm_health IN ('KC19', 'KC21'))
                                AND elig.sak_recip = asgn.sak_recip
                                AND elig.sak_pgm_elig = asgn.sak_pgm_elig
                                AND asgn.cde_rsn_mc_stop in ('58', '59', '60')
                                AND elig.dte_end = TO_CHAR(last_day(ADD_MONTHS(TO_DATE(:dte_current_benefit_month,'YYYYMMDD'),-1)),'YYYYMMDD')
                                AND elig.cde_status1 <> 'H';

                            if (sqlca.sqlcode != ANSI_SQL_OK)
                            {
                                fprintf (stderr, "%s - ERROR: Could not count KanCare t_re_pmp_assign records ('58', '59', '60') for sak_recip [%d]!\n", FNC, sql_pntl_rcd.sak_recip);
                                sqlerror();
                                exit(FAILURE);
                            }

                            if ( (sql_count > 0) && (after_month_end == mgd_TRUE) && (daily_retro_dte_effective == dte_current_benefit_month) )
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\t\tPrevious PMP found ending on day prior to [%d] and after month end - setting "
                                    "start reason to '96 - Default - Continuity of Plan'.\n", dte_current_benefit_month);
                                strcpy(tmp_start_rsn, START_RSN_96_CONTINUITY_OF_PLAN);
                                tmp_mc_entity = (tmp_sak_pub_hlth == sak_pub_hlth_kc21) ? SAK_ENT_AUTOASSIGN_PREV_KC_PMP_PGM_CHG_KC21 : SAK_ENT_AUTOASSIGN_PREV_KC_PMP_PGM_CHG_KC19;
                            }
                            else
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\t\tPrevious PMP NOT found ending on day prior to [%d] and after month end.\n", dte_current_benefit_month);
                                if (assigned_in_last_3_months == mgd_TRUE)
                                {
                                    fprintf(err_rpt_file_ptr, "\t\t\t\tPrevious PMP found was <= 3 months in the past - setting start reason to '93 - 90 Day Retro-reattach'.\n");
                                    strcpy(tmp_start_rsn, START_RSN_93_90_DAY_RETRO_REATTACH);
                                    tmp_mc_entity = (tmp_sak_pub_hlth == sak_pub_hlth_kc21) ? SAK_ENT_AUTOASSIGN_90_DAY_RETRO_REATTACH_KC21 : SAK_ENT_AUTOASSIGN_90_DAY_RETRO_REATTACH_KC19;
                                }
                                else
                                {
                                    fprintf(err_rpt_file_ptr, "\t\t\t\tPrevious PMP found was > 3 months in the past - setting start reason to '94 - Previous Assignment'.\n");
                                    strcpy(tmp_start_rsn, START_RSN_94_PREVIOUS_ASSIGNMENT);
                                    tmp_mc_entity = SAK_ENT_AUTOASSIGN_PREV_KC_PMP_180_DAY;
                                }
                            }
                            prev_pmp_180_cnt++;
                        }

                    } /* End of 180 Day logic*/



                    /****************************************************************************/
                    /*                      Case Logic for KC19...                              */
                    /****************************************************************************/
                    if ( (recip_assigned == mgd_FALSE) && (daily_retro_bad_date == mgd_FALSE) && (tmp_sak_pmp_ser_loc <= 0) )
                    {
                        fprintf(err_rpt_file_ptr, "\t\t\tDaily Case Processing...\n");

                        tmp_sak_pmp_ser_loc = get_case_pmp(&tmp_sak_pub_hlth, sql_kc_elig.dte_benefit_month);

                        if ( tmp_sak_pmp_ser_loc > 0 )
                        {
                            daily_retro_dte_effective = sql_kc_elig.dte_benefit_month;
                            daily_retro_dte_end = c_get_month_end(sql_kc_elig.dte_benefit_month);
                            strcpy(tmp_start_rsn, START_RSN_86_CASE_CONTINUITY);
                            tmp_mc_entity = (tmp_sak_pub_hlth == sak_pub_hlth_kc21) ? SAK_ENT_AUTOASSIGN_CASE_KC21 : SAK_ENT_AUTOASSIGN_CASE_KC19;
                            case_logic_cnt++;
                        }

                    } /* End of Case logic*/



                    /****************************************************************************/
                    /*                   Default Logic for                                      */
                    /****************************************************************************/
                    if ( (recip_assigned == mgd_FALSE) && (daily_retro_bad_date == mgd_FALSE) && (tmp_sak_pmp_ser_loc <= 0) )
                    {
                        if (arg_rsn_rpting == mgd_TRUE)
                            fprintf(err_rpt_file_ptr, "\t\t\tDaily Default Processing...\n");

                        tmp_sak_pmp_ser_loc = get_morbidity_mco(&tmp_sak_pub_hlth);

                        if ( tmp_sak_pmp_ser_loc > 0 )
                        {
                            daily_retro_dte_effective = sql_kc_elig.dte_benefit_month;
                            daily_retro_dte_end = c_get_month_end(sql_kc_elig.dte_benefit_month);
                            strcpy(tmp_start_rsn, START_RSN_92_MORBIDITY);
                            tmp_mc_entity = (tmp_sak_pub_hlth == sak_pub_hlth_kc21) ? SAK_ENT_AUTOASSIGN_MORBIDITY_KC21 : SAK_ENT_AUTOASSIGN_MORBIDITY_KC19;
                            morbidity_cnt++;
                        }

                    } /* End of Morbidity Logic */

                    bene_prev_dte_benefit_month = sql_kc_elig.dte_benefit_month;

                    kc_elig_cursor_rc = fetch_kc_elig();
                }


                // ********************************************************************************************************************************
                // ********************************************************************************************************************************
                // If a PMP was found, but an assignment not yet created (because there was just one eligibility month), then make one.
                // NOTE:  The logic within the following 'if' statement MUST be identical to the code above marked DAILY_CODE_LOOP
                // 2_DAILY_CODE_LOOP_BEGIN
                // ********************************************************************************************************************************
                // ********************************************************************************************************************************
                if (tmp_sak_pmp_ser_loc > 0)
                {
                    if (daily_retro_dte_end != c_get_month_end(dte_kc_elig_verification))
                    {
                        fprintf(err_rpt_file_ptr, "\t\t\tLast eligibility month [%d] is NOT the end of the latest KAECSES month [%d] - setting end date to [%d]\n",
                            daily_retro_dte_end, c_get_month_end(dte_kc_elig_verification), daily_retro_dte_end);
                        // Override the end date logic that's in c_do_create_pmp_assign_2...
                        c_set_asgn_dte_end_override(daily_retro_dte_end);
                    }
                    else
                    {
                        fprintf(err_rpt_file_ptr, "\t\t\tLast eligibility month [%d] IS the end of the latest KAECSES month [%d] - allowing EOT end date.\n",
                            daily_retro_dte_end, c_get_month_end(dte_kc_elig_verification));
                       // strcpy(tmp_start_rsn, "99");
                    }

                    if (recip_dte_death > 0)
                    {
                        if (   (c_get_asgn_dte_end_override() > recip_dte_death) ||
                             ( (c_get_asgn_dte_end_override() == 0) && (recip_dte_death > 0) ) )
                        {
                            fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary has dte_death on file [%d] - resetting end date [%d] to death date.\n", recip_dte_death, c_get_asgn_dte_end_override());
                            c_set_asgn_dte_end_override(recip_dte_death);
                        }
                        else
                        {
                            fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary has dte_death on file [%d] - reset of end date [%d] to death date UNNECESSARY.\n", recip_dte_death, c_get_asgn_dte_end_override());
                        }
                    }

                    if (sql_pntl_rcd.sak_pub_hlth == 81)
                    {
                        /*  Note, the logic below evolved to the point of what is shown here; however,
                            the existing code was left in case we need to revert back.
                                if (recip_ptl_add_date_used == mgd_FALSE)
                                {
                                    daily_retro_dte_effective = sql_pntl_rcd.dte_added;
                                    recip_ptl_add_date_used = mgd_TRUE;
                                }
                        */
                        if ( (daily_retro_dte_effective == dte_kc_elig_verification) && (recip_ptl_add_date_used == mgd_FALSE) )
                        {
                            daily_retro_dte_effective = sql_pntl_rcd.dte_added;
                            recip_ptl_add_date_used = mgd_TRUE;
                            fprintf(err_rpt_file_ptr, "\t\t\tKC21 Beneficiary benefit month is for current benefit month [%d] - set dte_effective to pntl add date [%d].\n",
                                dte_kc_elig_verification, daily_retro_dte_effective);
                        }
                        else
                        {
                            if ( (c_get_next_month_beg(daily_retro_dte_effective) == dte_kc_elig_verification) &&
                                 (after_month_end == mgd_TRUE) &&
                                 (recip_ptl_add_date_used == mgd_FALSE) )
                            {
                                daily_retro_dte_effective = sql_pntl_rcd.dte_added;
                                recip_ptl_add_date_used = mgd_TRUE;
                                fprintf(err_rpt_file_ptr, "\t\t\tKC21 Beneficiary benefit month is for next benefit month [%d] and after month end  - set dte_effective to pntl add date [%d].\n",
                                    dte_kc_elig_verification, daily_retro_dte_effective);
                            }
                            else
                            {
                                if (UseKEES_Logic == 1 )
                                {
                                    if (recip_ptl_add_date_used == mgd_FALSE)
                                    {
                                        daily_retro_dte_effective = sql_pntl_rcd.dte_added;
                                        recip_ptl_add_date_used = mgd_TRUE;
                                        fprintf(err_rpt_file_ptr, "\t\t\tKC21 KEES day specific retro - set dte_effective to pntl add date [%d].\n",
                                            daily_retro_dte_effective);
                                    }
                                    else
                                    {
                                        fprintf(err_rpt_file_ptr, "\t\t\tKC21 pntl add date already used [%d] - utilizing benefit month [%d] for asgn effective date.\n",
                                        sql_pntl_rcd.dte_added, dte_kc_elig_verification);
                                    }
                                }
                                else
                                {
                                    fprintf(err_rpt_file_ptr, "\t\t\tKC21 Beneficiary benefit month is NOT for current benefit month [%d] - utilizing benefit month [%d] for asgn effective date.\n",
                                        dte_kc_elig_verification, daily_retro_dte_effective);
                                }
                            }
                        }
                    }

                    // For presumptive eligibles, we will always use the potential date for the effective date...
                    if (recip_is_presumptive == mgd_TRUE)
                    {
                        if (sql_pntl_rcd.dte_added >= sql_dte_kc_golive)
                        {
                            if (recip_ptl_add_date_used == mgd_FALSE)
                            {
                                recip_ptl_add_date_used = mgd_TRUE;
                                fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary is presumptive and dte_added >= KC golive.  Changing effective date from [%d] to [%d]\n", daily_retro_dte_effective, sql_pntl_rcd.dte_added);
                                daily_retro_dte_effective = sql_pntl_rcd.dte_added;
                            }
                            else
                            {
                                fprintf(err_rpt_file_ptr, "\t\t\tPresumptive pntl add date already used [%d] - utilizing benefit month [%d] for asgn effective date.\n", sql_pntl_rcd.dte_added, dte_kc_elig_verification);
                            }
                        }
                        else
                        {
                            fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary is presumptive and dte_added < KC golive.  Changing effective date from [%d] to [%d]\n", daily_retro_dte_effective, sql_dte_kc_golive);
                            daily_retro_dte_effective = sql_dte_kc_golive;
                        }
                    }
                    else
                    {
                        fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary is NOT presumptive.  Leaving effective date as [%d]\n", daily_retro_dte_effective);
                    }

                    if ((recip_dte_death > 0) && (recip_dte_death <= daily_retro_dte_effective))
                    {
                        fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary DOD [%d] is prior to retro effective date [%d] - unable to create assignment.\n",
                            recip_dte_death, daily_retro_dte_effective);
                    }
                    else
                    {
                        fprintf(err_rpt_file_ptr, "\t\t\tBeneficiary DOD [%d] is NOT prior to retro effective date [%d] - creating assignment.\n",
                            recip_dte_death, daily_retro_dte_effective);
                        make_pmp_assgn(tmp_sak_pmp_ser_loc, tmp_start_rsn,
                                      tmp_mc_entity,
                                      daily_retro_dte_effective, sql_pntl_rcd.sak_pub_hlth, 0, " ");
                    }

                    tmp_sak_pmp_ser_loc = 0;
                }
                // ********************************************************************************************************************************
                // ********************************************************************************************************************************
                // 2_DAILY_CODE_LOOP_END
                // ********************************************************************************************************************************
                // ********************************************************************************************************************************

                EXEC SQL close kc_elig_cursor ;

                if (sqlca.sqlcode != ANSI_SQL_OK)
                {
                    fprintf (stderr, "  ERROR: could not close the kc_elig_cursor Cursor\n");
                    sqlerror();
                    exit(FAILURE);
                }
            }
        }
        else
        {
            if (arg_rsn_rpting == mgd_TRUE)
                fprintf(err_rpt_file_ptr, "\tKanCare Monthly Processing...\n");
            /******************************************************************************/
            /*                            KanCare XIX Logic...                            */
            /******************************************************************************/
            if (sql_pntl_rcd.sak_pub_hlth == sak_pub_hlth_kc19)
            {
                /****************************************************************************/
                /*                  Determine eligibility...                                */
                /****************************************************************************/
                if (arg_rsn_rpting == mgd_TRUE)
                    fprintf(err_rpt_file_ptr, "\tDetermining eligibility for KC19...\n" );

                sql_dte_enroll_pgm = det_pgm_assign_dte(sak_pub_hlth_kc19);
                //rc_elig = check_pgm_elig(sak_pub_hlth_kc19, dte_kc_elig_verification);
    //            rc_elig = check_pgm_elig(sak_pub_hlth_kc19, sql_dte_enroll_pgm);
    //            if ( rc_elig == mgd_TRUE )
    //                eligible_for_kc19 = mgd_TRUE;
    //            else
    //                eligible_for_kc19 = mgd_FALSE;
                eligible_for_kc19 = mgd_TRUE;

                /* if the bene is not eligible for the specified pgm, then remove the potential record... */
                if ( (sql_pntl_rcd.sak_pub_hlth == sak_pub_hlth_kc19) && (eligible_for_kc19 == mgd_FALSE) )
                {
                    cnt_not_elig++;
                    delete_pot_record();
                    recip_assigned = mgd_TRUE;
                }


                /****************************************************************************/
                /*                  Newborn Logic.......                                    */
                /****************************************************************************/
                if ( recip_assigned == mgd_FALSE &&
                     recip_is_newborn == mgd_TRUE )

                {
                    if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tMonthly Newborn Processing...\n");
                    assign_newborn();

                }

                /****************************************************************************/
                /*                     MCO Choice Logic...                                  */
                /****************************************************************************/
                if ( recip_assigned == mgd_FALSE )
                {
                     if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tMonthly MCO Choice Processing...\n");

                    assign_mco_choice();
                }

                /****************************************************************************/
                /*                     Previous KC Assignment Logic for KC19...             */
                /****************************************************************************/
                // See if the beneficiary has a previous KC assignment that was Historied due
                // to the beneficiary losing eligibility.  If that happened because the beneficiary
                // went from TXXI to TXIX, then we should try to assign them to the same MCO that
                // they previously had.
                if ( recip_assigned == mgd_FALSE )
                {
                     if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tMonthly Previous KC PMP (historied) Processing...\n");

                    assign_prev_kc_pmp();
                }


                /****************************************************************************/
                /*                      Previous PMP 180 Logic for KC19...                  */
                /****************************************************************************/
                if ( recip_assigned == mgd_FALSE )
                {
                     if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tMonthly Previous PMP (180) Processing...\n");

                    assign_prev_kc_pmp_180_day();

                } /* End of 180 Day logic*/



                /****************************************************************************/
                /*                      Case Logic for KC19...                              */
                /****************************************************************************/
                if ( recip_assigned == mgd_FALSE )
                {
                     if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tMonthly Case Processing...\n");

                    assign_case_pmp();

                } /* End of Case logic*/



                /****************************************************************************/
                /*                   Default Logic for                                      */
                /****************************************************************************/
                if (recip_assigned == mgd_FALSE)
                {
                    if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tMonthly Default Processing...\n");

                    assign_morbidity();

                } /* End of Morbidity Logic */


                if (recip_assigned == mgd_FALSE)
                {

                    if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tLOGIC ERROR:  Was not able to assign beneficiary!\n");
                }


            } /* End of pub hlth was KC19 on potential record */
            else
            {
                /****************************************************************************/
                /*                  Determine eligibility...                                */
                /****************************************************************************/
                if (arg_rsn_rpting == mgd_TRUE)
                    fprintf(err_rpt_file_ptr, "\tDetermining eligibility for KC21...\n" );

                sql_dte_enroll_pgm = det_pgm_assign_dte(sak_pub_hlth_kc21);
                //rc_elig = check_pgm_elig(sak_pub_hlth_kc21, dte_kc_elig_verification);
                rc_elig = check_pgm_elig(sak_pub_hlth_kc21, sql_dte_enroll_pgm);
                if ( rc_elig == mgd_TRUE )
                    eligible_for_kc21 = mgd_TRUE;
                else
                    eligible_for_kc21 = mgd_FALSE;

                /* if the bene is not eligible for the specified pgm, then remove the potential record... */
                if ( (sql_pntl_rcd.sak_pub_hlth == sak_pub_hlth_kc21) && (eligible_for_kc21 == mgd_FALSE) )
                {
                    cnt_not_elig++;
                    delete_pot_record();
                    recip_assigned = mgd_TRUE;
                }

                /****************************************************************************/
                /*                  Newborn Logic.......                                    */
                /****************************************************************************/
                if ( recip_assigned == mgd_FALSE &&
                     recip_is_newborn == mgd_TRUE )

                {
                    if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tMonthly Newborn Processing...\n");
                    assign_newborn();

                }

                /****************************************************************************/
                /*                     MCO Choice Logic...                                  */
                /****************************************************************************/
                if ( recip_assigned == mgd_FALSE )
                {
                     if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tMonthly MCO Choice Processing...\n");

                    assign_mco_choice();
                }

                /****************************************************************************/
                /*                     Previous KC Assignment Logic for KC21...             */
                /****************************************************************************/
                // See if the beneficiary has a previous KC assignment that was Historied due
                // to the beneficiary losing eligibility.  If that happened because the beneficiary
                // went from TXIX to TXXI, then we should try to assign them to the same MCO that
                // they previously had.
                if ( recip_assigned == mgd_FALSE )
                {
                     if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tMonthly Previous KC PMP (historied) Processing...\n");

                    assign_prev_kc_pmp();
                }



                /****************************************************************************/
                /*                      Previous PMP 180 Logic for KC19...                  */
                /****************************************************************************/
                if ( recip_assigned == mgd_FALSE )
                {
                     if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tMonthly Previous PMP (180) Processing...\n");

                    assign_prev_kc_pmp_180_day();

                } /* End of 180 Day logic*/


                /****************************************************************************/
                /*                      Case Logic for KC21...                              */
                /****************************************************************************/
                if ( recip_assigned == mgd_FALSE )
                {
                     if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tMonthly Case Processing...\n");

                    assign_case_pmp();

                } /* End of Case logic*/


                /****************************************************************************/
                /*                   Default Logic for                                      */
                /****************************************************************************/
                if (recip_assigned == mgd_FALSE)
                {
                    if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tMonthly Default Processing...\n");

                    assign_morbidity();

                } /* End of Morbidity Logic */


                if (recip_assigned == mgd_FALSE)
                {
                    if (arg_rsn_rpting == mgd_TRUE)
                        fprintf(err_rpt_file_ptr, "\tLOGIC ERROR:  Was not able to assign beneficiary!\n");
                }
            }
        }
        /* COMMIT or ROLLBACK the changes depending on the command line parm... */
        // Due to the way the
        //save_to_db(FNC);
        cursor_rc = fetch_pot_info();

    }  /* end of fetch cursor while loop */

    ind_abend = mgd_FALSE;
    display_final_counts();

//    if (arg_save_to_db == mgd_TRUE)
//        delete_kc_ptl_records();

    update_sys_parms();


    /* Perform a final commit or full rollback, depending on the command line parm... */
    if (arg_save_to_db == mgd_TRUE)
    {
        fprintf(stderr, "arg_save_to_db == mgd_TRUE - commiting changes to database.\n");
        strcpy(tmp_type, "COMMIT");
        EXEC SQL COMMIT;
    }
    else
    {
        fprintf(stderr, "arg_save_to_db != mgd_TRUE - rolling back changes to database.\n");
        strcpy(tmp_type, "ROLLBACK");
        EXEC SQL ROLLBACK;
    }

    if (sqlca.sqlcode != 0)
    {
        fprintf(stderr, "DATABASE ERROR in %s:  %s FAILED!!!\n", FNC, tmp_type);
        sqlerror();
        exit(FAILURE);
    }

    finalize();

    exit(0);
}

/****************************************************************************/
/*                                                                          */
/* Function:    update_sys_parms()                                          */
/*                                                                          */
/* Description: Retrieve environment variables, open files,                 */
/*              connect to database, retrieve appropriate dates,            */
/*              open main cursor.                                           */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- ------------------------------          */
/*                                                                          */
/****************************************************************************/
static void update_sys_parms(void)
{
    char FNC[] = "update_sys_parms()";

   EXEC SQL BEGIN DECLARE SECTION;
      int sql_mco_choice_latest_run_date = 0;
      int sql_mco_choice_latest_run_time = 0;
      long sql_mco_choice_latest_run_datetime = 0;
      int sql_kc_elig_latest_run_date = 0;
      int sql_kc_elig_latest_run_time = 0;
      long sql_kc_elig_latest_run_datetime = 0;
   EXEC SQL END DECLARE SECTION;

    EXEC SQL
     SELECT TO_CHAR(MAX(dte_added), 'YYYYMMDD'), TO_CHAR(MAX(dte_added), 'HH24MISS'), TO_CHAR(MAX(dte_added), 'YYYYMMDDHH24MISS')
       INTO :sql_mco_choice_latest_run_date, :sql_mco_choice_latest_run_time, :sql_mco_choice_latest_run_datetime
       FROM t_mc_mco_choice;

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: Could not select MAX(dte_added) record from T_MC_MCO_CHOICE!\n", FNC);
        sqlerror();
        exit(FAILURE);
    }

    fprintf(stderr, "The previous MCO Choice run date/time is.... [%d]/[%d] - [%ld]\n",
        sql_mco_choice_last_run_date, sql_mco_choice_last_run_time, sql_mco_choice_last_run_datetime);
    fprintf(stderr, "The latest run date/time is..... [%d]/[%d] - [%ld]\n",
        sql_mco_choice_latest_run_date, sql_mco_choice_latest_run_time, sql_mco_choice_last_run_datetime);

    EXEC SQL
      UPDATE t_system_parms
         SET dte_parm_2 = :sql_mco_choice_latest_run_date,
             tme_assigned = :sql_mco_choice_latest_run_time,
             date_parm_1 = :sql_mco_choice_last_run_date,
             time_system = :sql_mco_choice_last_run_time
       WHERE nam_program = 'MGDCHCLR';

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: Could not update T_SYSTEM_PARMS record for nam_program [MGDCHCLR]!\n", FNC);
        sqlerror();
        exit(FAILURE);
    }


    if (arg_run_level == DAILY)
    {
        EXEC SQL
         SELECT TO_CHAR(MAX(dte_added), 'YYYYMMDD'), TO_CHAR(MAX(dte_added), 'HH24MISS'), TO_CHAR(MAX(dte_added), 'YYYYMMDDHH24MISS')
           INTO :sql_kc_elig_latest_run_date, :sql_kc_elig_latest_run_time, :sql_kc_elig_latest_run_datetime
           FROM t_mc_kc_eligibility;

        if (sqlca.sqlcode != ANSI_SQL_OK)
        {
            fprintf (stderr, "%s - ERROR: Could not select MAX(dte_added) record from T_MC_KC_ELIGIBILITY!\n", FNC);
            sqlerror();
            exit(FAILURE);
        }

        fprintf(stderr, "The previous KC Elig run date/time  (sql_kc_elig_last_run_datetime) is.... [%d]/[%d] - [%ld]\n",
            sql_kc_elig_last_run_date, sql_kc_elig_last_run_time, sql_kc_elig_last_run_datetime);
        fprintf(stderr, "The latest run date/time (sql_kc_elig_last_run_datetime) is..... [%d]/[%d] - [%ld]\n",
            sql_kc_elig_latest_run_date, sql_kc_elig_latest_run_time, sql_kc_elig_last_run_datetime);

        EXEC SQL
          UPDATE t_system_parms
             SET dte_parm_2 = :sql_kc_elig_latest_run_date,
                 tme_assigned = :sql_kc_elig_latest_run_time,
                 date_parm_1 = :sql_kc_elig_last_run_date,
                 time_system = :sql_kc_elig_last_run_time
           WHERE nam_program = 'MGDKCELR';

        if (sqlca.sqlcode != ANSI_SQL_OK)
        {
            fprintf (stderr, "%s - ERROR: Could not update T_SYSTEM_PARMS record for nam_program [MGDKCELR]!\n", FNC);
            sqlerror();
            exit(FAILURE);
        }
    }
}

/****************************************************************************/
/*                                                                          */
/* Function:    initialize()                                                */
/*                                                                          */
/* Description: Retrieve environment variables, open files,                 */
/*              connect to database, retrieve appropriate dates,            */
/*              open main cursor.                                           */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- ------------------------------          */
/*                                                                          */
/****************************************************************************/
static void initialize(int argnum, char *argmnts[])
{
    char FNC[] = "initialize()";
    int rc = 0;
    int status = 0;
    struct mc_elig_stop_rsn elig_stop_str;
    struct mc_entity entity_str;
    struct mc_rtn_message rtn_msg_str;
    struct mc_system_parms parms_str;
    struct mc_dates mc_dates_str;
//    int   first_of_next_month = 0;

    process_cmd_line(argnum, argmnts);

    if (( err_rpt_file = getenv( "dd_ERRORRPT_FILE" )) == NULL )
    {
        fprintf(stderr, "  ERROR: dd_ERRORRPT_FILE was not set.\n");
        exit(FAILURE);
    }

    if (( err_rpt_file_ptr = fopen( err_rpt_file, "w" )) == NULL )
    {
        fprintf(stderr, "   ERROR:  Unable to open Error Report "
                       "file [%s]\n", err_rpt_file);
        exit(FAILURE);
    }

    if (( nb_rpt_file = getenv( "dd_NEWBORNRPT_FILE" )) == NULL )
    {
        fprintf(stderr, "  ERROR: dd_NEWBORNRPT_FILE was not set.\n");
        exit(FAILURE);
    }

    if (( nb_rpt_file_ptr = fopen( nb_rpt_file, "w" )) == NULL )
    {
        fprintf(stderr, "   ERROR:  Unable to open New Born Report data file "
                       "file [%s]\n", err_rpt_file);
        exit(FAILURE);
    }

    if ((rc = ConnectToDB()) == FAILURE)
        exit(FAILURE);

    // Retrieve the current cycle date...
    EXEC SQL SELECT dte_parm_2, TO_CHAR(ADD_MONTHS(TO_DATE(dte_parm_2, 'YYYYMMDD'), - 2), 'YYYYMM') || '01', TO_CHAR(ADD_MONTHS(TO_DATE(dte_parm_2, 'YYYYMMDD'), - 3), 'YYYYMM') || '01'
             INTO   :dte_current, :dte_current_minus_2_mnths, :dte_current_minus_3_mnths
             FROM   t_system_parms
             WHERE  nam_program = 'AIMCYCLE';

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: could not retrieve T_SYSTEM_PARMS record "
                "for nam_program [%s]\n", FNC, "AIMCYCLE");
        sqlerror();
        exit(FAILURE);
    }

// Use the following to override the current date for testing purposes:
//    dte_current = 20130430;
//    dte_current_minus_2_mnths = 20130201;
//    dte_current_minus_3_mnths = 20130101;
//
//    fprintf(stderr, "\n\nFORCED CURRENT DATE TO [%d], CURRENT DATE MINUS 2 MONTHS [%d], CURRENT DATE MINUS 3 MONTHS [%d]\n\n\n",
//        dte_current, dte_current_minus_2_mnths, dte_current_minus_3_mnths);
//
//    fprintf(err_rpt_file_ptr, "\n\n\n\n*** FORCED CURRENT DATE TO [%d], CURRENT DATE MINUS 2 MONTHS [%d], CURRENT DATE MINUS 3 MONTHS [%d] ***\n\n\n\n",
//        dte_current, dte_current_minus_2_mnths, dte_current_minus_3_mnths);

    // Retrieve the assignment effective date for the initial KanCare enrollments...
    EXEC SQL SELECT date_parm_1
             INTO   :sql_dte_kc_golive
             FROM   t_system_parms
             WHERE  nam_program = 'MGDKCBGN';

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: could not retrieve T_SYSTEM_PARMS record "
                "for nam_program [%s]\n", FNC, "MGDKCBGN");
        sqlerror();
        exit(FAILURE);
    }

    // Since the 24hr time is being stored in a numeric field, any leading zeros will get stripped off during storage,
    // so it needs to be padded with zeros before joining it together with the date...
    EXEC SQL
     SELECT dte_parm_2, tme_assigned,
            to_char(to_date(dte_parm_2, 'YYYYMMDD'), 'YYYYMMDD') || to_char(to_date(substr('000000'||tme_assigned,-6), 'HH24MISS'),'HH24MISS')
       INTO :sql_mco_choice_last_run_date, :sql_mco_choice_last_run_time,
            :sql_mco_choice_last_run_datetime
       FROM t_system_parms
      WHERE nam_program  = 'MGDCHCLR';

    if (sqlca.sqlcode == ANSI_SQL_NOTFOUND)
    {
        fprintf (stderr, "\n%s - NOT FOUND: Retrieving T_SYSTEM_PARM record for nam_program [MGDCHCLR].\n"
                "This record is used to store the date and time for the PREVIOUS MCO Choice update last run.\n", FNC);
        fprintf(stderr, "Inserting new record using date prior to minimum t_mco_choice.dte_added date minus 1 day.\n");

        EXEC SQL
         SELECT TO_CHAR(MIN(dte_added - 1), 'YYYYMMDD'), TO_CHAR(MIN(dte_added - 1), 'HH24MISS'), TO_CHAR(MIN(dte_added - 1), 'YYYYMMDDHH24MISS')
           INTO :sql_mco_choice_last_run_date, :sql_mco_choice_last_run_time, :sql_mco_choice_last_run_datetime
           FROM t_mc_mco_choice;

        if (sqlca.sqlcode != ANSI_SQL_OK)
        {
            fprintf (stderr, "%s - ERROR: Could not select MIN(dte_added) record from T_MC_MCO_CHOICE!\n", FNC);
            sqlerror();
            exit(FAILURE);
        }

        EXEC SQL
         INSERT INTO t_system_parms
         VALUES ('MGDCHCLR', 19000101, 22991231, ' ', :sql_mco_choice_last_run_date,
                 :sql_mco_choice_last_run_date, :sql_mco_choice_last_run_time,
                 :sql_mco_choice_last_run_time, 'MCO Choice Last Update', ' ', ' ');

    }
    else
    {
        if (sqlca.sqlcode != ANSI_SQL_OK)
        {
            fprintf (stderr, "\n%s - ERROR: Retrieving T_SYSTEM_PARM record for nam_program [MGDCHCLR].\n"
                    "This record is used to store the date and time for the PREVIOUS MCO Choice update last run.\n", FNC);
            sqlerror();
            exit(FAILURE);
        }
    }

    fprintf(stderr, "\n%s - sql_mco_choice_last_run_date as defined by T_SYSTEM_PARM.DTE_PARM_2 record for NAM_PROGRAM = 'MGDCHCLR' is [%d]\n", FNC, sql_mco_choice_last_run_date);
    fprintf(stderr, "%s - sql_mco_choice_last_run_time as defined by T_SYSTEM_PARM.TME_ASSIGNED record for NAM_PROGRAM = 'MGDCHCLR' is  [%d]\n", FNC, sql_mco_choice_last_run_time);
    fprintf(stderr, "%s - sql_mco_choice_last_run_datetime as defined by combining sql_mco_choice_last_run_date and sql_mco_choice_last_run_time is  [%ld]\n\n", FNC, sql_mco_choice_last_run_datetime);


    // Since the 24hr time is being stored in a numeric field, any leading zeros will get stripped off during storage,
    // so it needs to be padded with zeros before joining it together with the date...
    EXEC SQL
     SELECT dte_parm_2, tme_assigned,
            to_char(to_date(dte_parm_2, 'YYYYMMDD'), 'YYYYMMDD') || to_char(to_date(substr('000000'||tme_assigned,-6), 'HH24MISS'),'HH24MISS')
       INTO :sql_kc_elig_last_run_date, :sql_kc_elig_last_run_time,
            :sql_kc_elig_last_run_datetime
       FROM t_system_parms
      WHERE nam_program  = 'MGDKCELR';

    if (sqlca.sqlcode == ANSI_SQL_NOTFOUND)
    {
        fprintf (stderr, "\n%s - NOT FOUND: Retrieving T_SYSTEM_PARM record for nam_program [MGDKCELR].\n"
                "This record is used to store the date and time for the KanCare Eligibility update last run.\n", FNC);
        fprintf(stderr, "Inserting new record using date prior to minimum t_mc_kc_eligibility.dte_added date minus 1 day.\n");

        EXEC SQL
         SELECT TO_CHAR(MIN(dte_added - 1), 'YYYYMMDD'), TO_CHAR(MIN(dte_added - 1), 'HH24MISS'), TO_CHAR(MIN(dte_added - 1), 'YYYYMMDDHH24MISS')
           INTO :sql_kc_elig_last_run_date, :sql_kc_elig_last_run_time, :sql_kc_elig_last_run_datetime
           FROM t_mc_mco_choice;

        if (sqlca.sqlcode != ANSI_SQL_OK)
        {
            fprintf (stderr, "%s - ERROR: Could not select MIN(dte_added) record from T_MC_KC_ELIGIBILITY!\n", FNC);
            sqlerror();
            exit(FAILURE);
        }

        EXEC SQL
         INSERT INTO t_system_parms
         VALUES ('MGDKCELR', 19000101, 22991231, ' ', :sql_kc_elig_last_run_date,
                 :sql_kc_elig_last_run_date, :sql_kc_elig_last_run_time,
                 :sql_kc_elig_last_run_time, 'KC Elig Last Update', ' ', ' ');

    }
    else
    {
        if (sqlca.sqlcode != ANSI_SQL_OK)
        {
            fprintf (stderr, "\n%s - ERROR: Retrieving T_SYSTEM_PARM record for nam_program [MGDKCELR].\n"
                    "This record is used to store the date and time for the PREVIOUS KC Elig update last run.\n", FNC);
            sqlerror();
            exit(FAILURE);
        }
    }

    fprintf(stderr, "\n%s - sql_kc_elig_last_run_date as defined by T_SYSTEM_PARM.DTE_PARM_2 record for NAM_PROGRAM = 'MGDKCELR' is [%d]\n", FNC, sql_kc_elig_last_run_date);
    fprintf(stderr, "%s - sql_kc_elig_last_run_time as defined by T_SYSTEM_PARM.TME_ASSIGNED record for NAM_PROGRAM = 'MGDKCELR' is  [%d]\n", FNC, sql_kc_elig_last_run_time);
    fprintf(stderr, "%s - sql_kc_elig_last_run_datetime as defined by combining sql_kc_elig_last_run_date and sql_kc_elig_last_run_time is  [%ld]\n\n", FNC, sql_kc_elig_last_run_datetime);


    // Retrieve the date used for verifying KanCare eligibility...
    EXEC SQL SELECT dte_parm_2, TO_CHAR((ADD_MONTHS(TO_DATE(dte_parm_2, 'YYYYMMDD'), -5)), 'YYYYMMDD')
             INTO   :dte_kc_elig_verification, :dte_t21_max_retro
             FROM   t_system_parms
             WHERE  nam_program = 'KCSESLRM';

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: could not retrieve T_SYSTEM_PARMS record "
                "for nam_program [%s]\n", FNC, "KCSESLRM");
        sqlerror();
        exit(FAILURE);
    }

    dte_current_benefit_month = dte_kc_elig_verification;
    dte_previous_benefit_month = c_get_month_begin_prev(dte_current_benefit_month);

    // Retrieve the date for the latest MCO morbidity stored...
    EXEC SQL SELECT MAX(dte_effective)
             INTO   :sql_max_dte_morbidity
             FROM   t_mc_re_morbidity;

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: could not retrieve MAX(dte_effective) from T_MC_RE_MORBIDITY!", FNC);
        sqlerror();
        exit(FAILURE);
    }


    /* Retrieve the current time... */
    rc = c_get_dte_current(&mc_dates_str, &rtn_msg_str);

    if ( rc == FUNCTION_SUCCESSFUL )
    {
         sprintf(curr_time, "%04d", mc_dates_str.time_hhmm);
         //first_of_next_month = mc_dates_str.fom_next;
    }
    else
    {
         c_do_display_rtn_msg( &rtn_msg_str, FNC);
         exit(FUNCTION_ERROR);
    }

    get_assign_dates();

    count_total_rcds();


    /************************/
    /* Open the main cursor */
    /************************/
    fprintf(stderr, "\nOpening all_potentials cursor...  ");
    EXEC SQL open all_potentials;

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "   ERROR: could not open the all_potentials Cursor\n");
        sqlerror();
        exit(FAILURE);
    }
    else
        fprintf(stderr, "Opened.\n\n");

    /* Retrieve the sak_elig_stop to use for Managed Care segments...  */
    rc = c_get_elig_stop_rsn(&elig_stop_str, &rtn_msg_str, 0, "   ");
    check_rc(&rtn_msg_str, rc, FNC);

    mc_sak_elig_stop = elig_stop_str.sak_elig_stop;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A01");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_CASE_KC21 = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A02");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_CASE_KC19 = entity_str.sak_mc_entity;



    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A09");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_MORBIDITY_KC19 = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A10");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_MORBIDITY_KC21 = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A11");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_PREV_KC_PMP_PGM_CHG_KC19 = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A12");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_PREV_KC_PMP_PGM_CHG_KC21 = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A13");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_PREV_KC_PMP_180_DAY = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A14");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_CHOICE_KC19 = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A15");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_CHOICE_KC21 = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A16");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_NEWBORN_KC19 = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A17");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_NEWBORN_KC21 = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A18");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_RETRO_KC19 = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A19");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_RETRO_KC21 = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A20");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_90_DAY_RETRO_REATTACH_KC19 = entity_str.sak_mc_entity;

    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "A21");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_AUTOASSIGN_90_DAY_RETRO_REATTACH_KC21 = entity_str.sak_mc_entity;



    /* Retrieve the number of months back to search for claims. */
    EXEC SQL SELECT date_parm_1
             INTO   :clm_srch_months
             FROM   t_system_parms
             WHERE  nam_program = 'MGDAACHM';

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: could not retrieve T_SYSTEM_PARMS record "
                "for nam_program [%s]\n", FNC, "MGDAACHM");
        sqlerror();
        exit(FAILURE);
    }

//    fprintf(err_rpt_file_ptr, "Number of months back to search claims history for HCBS [%d]\n",
//            clm_srch_months);

    /* Determine if using KEES logic */
    EXEC SQL SELECT date_parm_1
             INTO   :sql_tmp_parm
             FROM   t_system_parms
             WHERE  nam_program = 'KEESBGN';

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: could not retrieve T_SYSTEM_PARMS record "
                "for nam_program [%s]\n", FNC, "KEESBGN");
        sqlerror();
        exit(FAILURE);
    }
    else
    {
        if ( sql_tmp_parm <= mc_dates_str.dte)
        {
            UseKEES_Logic = 1;
            fprintf(err_rpt_file_ptr, "Using KEES logic, date[%d]\n", sql_tmp_parm );
            
        }
        else
        {
            UseKEES_Logic = 0;
            fprintf(err_rpt_file_ptr, "Not using KEES logic, date[%d]\n", sql_tmp_parm);
        }
        fprintf(stderr, "KEES logic is %s\n\n", (UseKEES_Logic) ? "enabled" : "disabled");
    }


    if (arg_save_to_db == mgd_TRUE)
        fprintf(err_rpt_file_ptr, "\n*** Updates WILL be saved to the database ***\n\n");
    else
        fprintf(err_rpt_file_ptr, "\n*** TEST MODE:  Updates will NOT be saved to the database!!! ***\n\n");

    fprintf(err_rpt_file_ptr, "\n*************************************************************\n");
    fprintf(err_rpt_file_ptr, "              BEGIN PROCESSING OF POTENTIAL TABLE");
    fprintf(err_rpt_file_ptr, "\n*************************************************************\n");

    fprintf(stderr, "\nmc_sak_elig_stop = [ %d ]\n", mc_sak_elig_stop);
    fprintf(stderr, "SAK_ENT_AUTOASSIGN_INELIG = [ %d ]\n", SAK_ENT_AUTOASSIGN_INELIG);

    fprintf(stderr, "recp_enroll_days = [ %d ]\n", recp_enroll_days);

    /* retrieve the time stamp from the operating system... */
    mark_date(date_stamp_initial);
    mark_time(time_stamp_initial);

    process_start_time = time(NULL);

    fflush(stderr);
    fflush(err_rpt_file_ptr);

}

/****************************************************************************/
/*                                                                          */
/* Function:     fetch_kc_elig()                                            */
/*                                                                          */
/* Description:  Retrieve the next Potential record to process.             */
/*                                                                          */
/****************************************************************************/
static int fetch_kc_elig(void)
{
    EXEC SQL FETCH kc_elig_cursor
           INTO  :sql_kc_elig.sak_short,
                 :sql_kc_elig.dte_benefit_month;

    if ( (sqlca.sqlcode != ANSI_SQL_OK) &&
         (sqlca.sqlcode != ANSI_SQL_NOTFOUND) )
    {
        fprintf(stderr, "  ERROR fetching into kc_elig_cursor cursor!\n");
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    return(sqlca.sqlcode);
}

/****************************************************************************/
/*                                                                          */
/* Function:     fetch_pot_info()                                           */
/*                                                                          */
/* Description:  Retrieve the next Potential record to process.             */
/*                                                                          */
/****************************************************************************/
static int fetch_pot_info(void)
{
    EXEC SQL FETCH all_potentials
           INTO  :sql_pntl_rcd.sak_recip,
                 :sql_pntl_rcd.sak_pub_hlth,
                 :sql_pntl_rcd.dte_added,
                 :sql_pntl_rcd.cde_reason,
                 :sql_pntl_rcd.cde_rsn_del,
                 :sql_pntl_rcd.sak_pmp_transfer,
                 :sql_pntl_rcd.dte_transfer_start,
                 :sql_pntl_rcd.sak_mc_ent_add,
                 :sql_pntl_rcd.cde_morbidity;

    if ( (sqlca.sqlcode != ANSI_SQL_OK) &&
         (sqlca.sqlcode != ANSI_SQL_NOTFOUND) )
    {
        fprintf(stderr, "  ERROR fetching into all_potentials cursor!\n");
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    if (sqlca.sqlcode == ANSI_SQL_OK)
        cnt_potential_read++;

    return(sqlca.sqlcode);
}

/****************************************************************************/
/*                                                                          */
/* Function:     fetch_rpt_info()                                           */
/*                                                                          */
/* Description:  Retrieve the next Potential record to process.             */
/*                                                                          */
/****************************************************************************/
static int fetch_rpt_info(void)
{
    EXEC SQL FETCH morbidity_rpt
           INTO  :sql_rpt_id_provider,
                 :sql_rpt_cde_service_loc,
                 :sql_rpt_cde_morbidity,
                 :sql_rpt_num_count;

    if ( (sqlca.sqlcode != ANSI_SQL_OK) &&
         (sqlca.sqlcode != ANSI_SQL_NOTFOUND) )
    {
        fprintf(stderr, "  ERROR fetching into fetch_rpt_info cursor!\n");
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }


    return(sqlca.sqlcode);
}

/****************************************************************************/
/*                                                                          */
/* Function:     get_recip_info()                                           */
/*                                                                          */
/* Description:  Retrieve additional information for the beneficiary        */
/*                                                                          */
/*                                                                          */
/****************************************************************************/
static void get_recip_info(void)
{
    int rc;
    char FNC[] = "get_recip_info()";

    EXEC SQL SELECT id_medicaid,
                    sak_case,
                    cde_county,
                    adr_zip_code,
                    dte_birth,
                    dte_death,
                    TO_CHAR(dte_application, 'YYYYMMDD'),
                    TO_CHAR(ADD_MONTHS(dte_application, - 3), 'YYYYMM') || '01',
                    cde_sex
                    //TO_CHAR(dte_application - 90, 'YYYYMM') || '01'
             INTO   :recip_id_medicaid,
                    :recip_sak_case,
                    :recip_cde_county,
                    :recip_zip_code,
                    :recip_dte_birth,
                    :recip_dte_death,
                    :recip_dte_application,
                    :recip_dte_application_minus_3_mnths,
                    :recip_cde_sex
             FROM   t_re_base
             WHERE  sak_recip = :sql_pntl_rcd.sak_recip;

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: could not retrieve T_RE_BASE record "
                "for sak_recip [%d]\n", FNC, sql_pntl_rcd.sak_recip);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    return;

}

/****************************************************************************/
/*                                                                          */
/* Function:     delete_pot_record()                                        */
/*                                                                          */
/* Description:  This function will delete the potential record.            */
/*                                                                          */
/****************************************************************************/
static void delete_pot_record()
{

   EXEC SQL BEGIN DECLARE SECTION;
      int sql_mc_ent_del = -1;
   EXEC SQL END DECLARE SECTION;

   sql_mc_ent_del = SAK_ENT_AUTOASSIGN_INELIG;


   EXEC SQL UPDATE t_re_mc_recip
                SET sak_mc_ent_del = :sql_mc_ent_del
               WHERE sak_recip = :sql_pntl_rcd.sak_recip AND
                    sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth ;

    if  ( sqlca.sqlcode != ANSI_SQL_OK && sqlca.sqlcode != ANSI_SQL_NOTFOUND)
    {
        fprintf(stderr, " DATABASE ERROR:\n"
               "    Update to t_re_mc_recip table before delete FAILED for "
               "sak_recip: [%d], sak_pub_hlth [%d]\n",
               sql_pntl_rcd.sak_recip, sql_pntl_rcd.sak_recip);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(-1);
    }


    EXEC SQL delete
             FROM   t_re_mc_recip
             WHERE  sak_recip = :sql_pntl_rcd.sak_recip AND
                    sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth ;

    if ((sqlca.sqlcode == ANSI_SQL_OK) || (sqlca.sqlcode == ANSI_SQL_NOTFOUND))
    {
        if (arg_rsn_rpting == mgd_TRUE)
           fprintf(err_rpt_file_ptr, "\tPotential table record deleted.\n");
        cnt_pot_delete++;
    }
    else
    {
        fprintf(stderr, " DATABASE ERROR:\n"
               "    Delete from t_re_mc_recip table FAILED for "
               "sak_recip: %d \n", sql_pntl_rcd.sak_recip);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(-1);
    }

}

/****************************************************************************/
/*                                                                          */
/* Function:     delete_kc_ptl_records()                                    */
/*                                                                          */
/* Description:  This function will delete the potential record.            */
/*                                                                          */
/****************************************************************************/
static void delete_kc_ptl_records(void)
{

   EXEC SQL BEGIN DECLARE SECTION;
      int sql_mc_ent_del = -1;
   EXEC SQL END DECLARE SECTION;

   sql_mc_ent_del = SAK_ENT_AUTOASSIGN_INELIG;

    fprintf(stderr, "Updating delete code on all KanCare potential records...  ");
    fflush(stderr);
   EXEC SQL UPDATE t_re_mc_recip
                SET sak_mc_ent_del = :sql_mc_ent_del
               WHERE sak_pub_hlth IN (80,81) ;

    if  ( sqlca.sqlcode != ANSI_SQL_OK && sqlca.sqlcode != ANSI_SQL_NOTFOUND)
    {
        fprintf(stderr, " DATABASE ERROR:\n"
               "    Update to t_re_mc_recip table for all KC records before delete FAILED\n");
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(-1);
    }
    fprintf(stderr, "Done.\n");
    fflush(stderr);

    EXEC SQL delete
             FROM   t_re_mc_recip
             WHERE   sak_pub_hlth IN (80,81) ;

    if ((sqlca.sqlcode == ANSI_SQL_OK) || (sqlca.sqlcode == ANSI_SQL_NOTFOUND))
    {
        if (arg_rsn_rpting == mgd_TRUE)
           fprintf(err_rpt_file_ptr, "\tPotential table records for KanCare deleted.\n");
        cnt_pot_delete++;
    }
    else
    {
        fprintf(stderr, " DATABASE ERROR:\n"
               "    Delete from t_re_mc_recip table FAILED for all KC programs.\n");
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(-1);
    }

}

/****************************************************************************/
/*                                                                          */
/* Function:     det_pgm_assign_dte()                                       */
/*                                                                          */
/* Description:  Determine the assignment date for the specified pgm        */
/*                                                                          */
/****************************************************************************/
static int det_pgm_assign_dte(int parm_sak_pub_hlth)
{
   EXEC SQL BEGIN DECLARE SECTION;
      static int sql_benefit_month = -1;
      static int first_time = mgd_TRUE;
   EXEC SQL END DECLARE SECTION;

    if (arg_run_level == DAILY)
    {
        EXEC SQL
         SELECT MAX(dte_benefit_month)
           INTO :sql_benefit_month
           FROM T_MC_KC_ELIGIBILITY
          WHERE sak_recip = :sql_pntl_rcd.sak_recip;

        if  (sqlca.sqlcode != ANSI_SQL_OK)
        {
            fprintf(stderr, " DATABASE ERROR:\n"
                   "    Retrieve of benefit month from T_MC_KC_ELIGIBILITY for sak_recip [%d] failed!\n", sql_pntl_rcd.sak_recip);
            sqlerror();
            ind_abend = mgd_TRUE;
            finalize();
            exit(-1);
        }
    }
    else
    {
        //sql_benefit_month = dte_kc19_enroll;
        sql_benefit_month = dte_kc_elig_verification;
    }

    return(sql_benefit_month);

}

/****************************************************************************/
/*                                                                          */
/* Function:    finalize()                                                  */
/*                                                                          */
/* Description: Close files and display summary information                 */
/*                                                                          */
/* SE               CO    Date      Description                             */
/* ---------------- ----- --------  ------------------------------------    */
/*                                                                          */
/****************************************************************************/
static void finalize(void)
{
    char final_status[50];



    fprintf(stderr, "\n\n");
    fprintf(stderr, "\tNumber of Potential records read ..................... [%9d]\n\n", cnt_potential_read);
    fprintf(stderr, "\tNumber of Potential records eligible ................. [%9d]\n", cnt_elig);
    fprintf(stderr, "\tNumber of Potential records not eligible.............. [%9d]\n", cnt_not_elig);
    fprintf(stderr, "\n\n");
    fprintf(stderr, "\tNumber of KC19 Assignments ........................... [%9d]\n", cnt_kc19_assign);
    fprintf(stderr, "\tNumber of KC21 Assignments ........................... [%9d]\n", cnt_kc21_assign);
    fprintf(stderr, "\n");
    fprintf(stderr, "\tNumber of Bad Programs (Deleted) ..................... [%9d]\n", cnt_bad_program);
    fprintf(stderr, "\tNumber of Potenial records deleted for ineligibility . [%9d]\n", cnt_pot_delete);

    if (ind_abend == mgd_TRUE)
        strcpy(final_status, "Abnormal End of Process");
    else
        strcpy(final_status, "Process Successfully Completed");


    fprintf(err_rpt_file_ptr, "\n\n*************************************************************\n");
    fprintf(err_rpt_file_ptr, "                END PROCESSING OF POTENTIAL TABLE\n");
    fprintf(err_rpt_file_ptr, "       FINAL STATUS is:  [%s]", final_status);
    fprintf(err_rpt_file_ptr, "\n*************************************************************\n\n");

    fprintf(err_rpt_file_ptr, "\n\n");
    fprintf(err_rpt_file_ptr, "\tNumber of Potential records read ......................... [%9d]\n\n", cnt_potential_read);
    fprintf(err_rpt_file_ptr, "\tNumber of Potential records eligible ..................... [%9d]\n", cnt_elig);
    fprintf(err_rpt_file_ptr, "\tNumber of Potential records not eligible.................. [%9d]\n", cnt_not_elig);
    fprintf(err_rpt_file_ptr, "\n\n");
    fprintf(err_rpt_file_ptr, "\tNumber of KC19 Assignments ............................... [%9d]\n", cnt_kc19_assign);
    fprintf(err_rpt_file_ptr, "\tNumber of KC21 Assignments ............................... [%9d]\n", cnt_kc21_assign);
    fprintf(err_rpt_file_ptr, "\tNumber of Case Assignments ............................... [%9d]\n", case_logic_cnt);
    fprintf(err_rpt_file_ptr, "\tNumber of Morbidity Assignments .......................... [%9d]\n", morbidity_cnt);
    fprintf(err_rpt_file_ptr, "\tNumber of Prev PMP (180) Assignments ..................... [%9d]\n", prev_pmp_180_cnt);
    fprintf(err_rpt_file_ptr, "\tNumber of Retro with Current MCO Assignments ............. [%9d]\n", retro_future_pmp_cnt);
    fprintf(err_rpt_file_ptr, "\tNumber of MCO Choice Assignments ......................... [%9d]\n", mco_choice_cnt);
    fprintf(err_rpt_file_ptr, "\tNumber of Morbidity Assignments .......................... [%9d]\n", morbidity_cnt);
    fprintf(err_rpt_file_ptr, "\n\n");
    fprintf(err_rpt_file_ptr, "\tCOUNTS BY START REASON:\n");
    fprintf(err_rpt_file_ptr, "\t86 - KanCare Default - Case Continuity ................... [%9d]\n", cnt_start_rsn_86);
    fprintf(err_rpt_file_ptr, "\t92 - KanCare Default - Morbidity ......................... [%9d]\n", cnt_start_rsn_92);
    fprintf(err_rpt_file_ptr, "\t93 - KanCare Default - 90 Day Retro-reattach ............. [%9d]\n", cnt_start_rsn_93);
    fprintf(err_rpt_file_ptr, "\t94 - KanCare Default - Previous Assignment ............... [%9d]\n", cnt_start_rsn_94);
    fprintf(err_rpt_file_ptr, "\t96 - Default - Continuity of Plan ........................ [%9d]\n", cnt_start_rsn_96);
    fprintf(err_rpt_file_ptr, "\t97 - Retro assignment .................................... [%9d]\n", cnt_start_rsn_97);
    fprintf(err_rpt_file_ptr, "\tA1 - Choice - Enrollment into KanCare via Medicaid App ... [%9d]\n", cnt_start_rsn_A1);
    fprintf(err_rpt_file_ptr, "\tUndefined  (coding error) ................................ [%9d]\n", cnt_start_rsn_undefined);
    fprintf(err_rpt_file_ptr, "\n\n");
    fprintf(err_rpt_file_ptr, "\tNumber of Bad Programs (Deleted) ......................... [%9d]\n", cnt_bad_program);
    fprintf(err_rpt_file_ptr, "\tNumber of Potenial records deleted for ineligibility ..... [%9d]\n", cnt_pot_delete);

    /*************************/
    /* Close the main cursor */
    /*************************/
    EXEC SQL close all_potentials ;

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "  ERROR: could not close the all_potentials Cursor\n");
        sqlerror();
        exit(FAILURE);
    }


    /*************************/
    /* Close the error file  */
    /*************************/
    if ( ferror(err_rpt_file_ptr) )
    {
        fprintf(stderr, "   ERROR:  An error occured while writing to the "
                      "Error file (%s)!\n", err_rpt_file);
        perror( "Error File" );
        exit(FAILURE);
    }
    else
        if ( fclose(err_rpt_file_ptr) )
        {
            fprintf(stderr, "   ERROR:  Unable to close the "
                          "Error file correctly (%s).\n", err_rpt_file);
            perror( "Error File" );
            exit(FAILURE);
        }


}

/****************************************************************************/
/*                                                                          */
/* Function Name:  process_cmd_line()                                       */
/*                                                                          */
/* Description: This function reads in the arguments from the command       */
/*              line and parses the parameters.                             */
/*                                                                          */
/****************************************************************************/
static void process_cmd_line(int argnum, char *arglst[] )
{
    int argcnt = 1;
    char temp_string[MAX_STRING+1];
    short int found_rsn_reporting = mgd_FALSE;
    short int found_save_to_db = mgd_FALSE;
    short int found_runlevel = mgd_FALSE;

    while ( argcnt < argnum )
    {
        if ( (strcasecmp("-r",arglst[argcnt])==0) && (argnum > argcnt + 1) )
        {
            if ( strcasecmp("TRUE",arglst[argcnt+1])==0 )
            {
                fprintf(stderr, "\n*** Reason Reporting (beneficiary eligibility and PMP mis-match reasons) is ON ***\n");
                arg_rsn_rpting = mgd_TRUE;
                argcnt++;
            }
            else
            {
                fprintf(stderr, "\n*** Reason Reporting (beneficiary eligibility and PMP mis-match reasons) is OFF ***\n");
                arg_rsn_rpting = mgd_FALSE;
                argcnt++;
            }

            found_rsn_reporting = mgd_TRUE;
        }
        else
            if ( (strcasecmp("-s",arglst[argcnt])==0) && (argnum > argcnt + 1) )
            {
                if ( strcasecmp("TRUE",arglst[argcnt+1])==0 )
                {
                    fprintf(stderr, "*** Database COMMITS are ON ***\n\n");
                    arg_save_to_db = mgd_TRUE;
                    argcnt++;
                }
                else
                {
                    fprintf(stderr, "*** Database COMMITS are OFF ***\n\n");
                    arg_save_to_db = mgd_FALSE;
                    argcnt++;
                }

                found_save_to_db = mgd_TRUE;
            }
            else
               if ( (strcasecmp("-l",arglst[argcnt])==0) && (argnum > argcnt + 1) )
               {
                   found_runlevel = mgd_TRUE;
                   if ( strcasecmp("DAILY",arglst[argcnt+1])==0 )
                   {
                       fprintf(stderr, "*** Running Daily ***\n\n");
                       arg_run_level= DAILY;
                       argcnt++;
                   }
                   else
                      if ( strcasecmp("MONTHLY",arglst[argcnt+1])==0 )

                      {
                          fprintf(stderr, "*** Running Monthly ***\n\n");
                          arg_run_level = MONTHLY;
                          argcnt++;
                      }
                      else
                         found_runlevel = mgd_FALSE;
               }
               else
               {
                   print_usage();
                   exit(EXIT_FAILURE);
               }

            argcnt++;

    } /* end while */

    /* TESTING */
    /*
    found_rsn_reporting = mgd_TRUE;
    found_runlevel = mgd_TRUE;
    found_save_to_db = mgd_TRUE;
    arg_run_level = DAILY;
    arg_save_to_db = mgd_TRUE;
    arg_rsn_rpting = mgd_TRUE;
    */
    /* TESTING END */

    if ( (found_rsn_reporting == mgd_FALSE) ||
         (found_save_to_db == mgd_FALSE) ||
         (found_runlevel == mgd_FALSE)
       )
    {
        print_usage();
        exit(EXIT_FAILURE);
    }
}

/****************************************************************************/
/*                                                                          */
/* Function Name:  print_usage()                                            */
/*                                                                          */
/* Description:  Prints out a usage message if the was an error parsing     */
/*               the command line parameters.                               */
/*                                                                          */
/*                                                                          */
/****************************************************************************/
static void print_usage(void)
{
     fprintf(stderr,"\n\n%s -r REASON_REPORTING -s SAVE_TO_DB -l DAILY | MONTHLY\n",PROGNAME);
     fputs  ("    Where:\n",stderr);
     fputs  ("       REASON_REPORTING - is either 'true' or 'false'\n",stderr);
     fputs  ("       SAVE_TO_DB       - is either 'true' or 'false'\n",stderr);
     fputs  ("       DAILY | MONTHLY  - run daily or monthly\n",stderr);
     fputs  ("\n\n",stderr);
}

/****************************************************************************/
/*                                                                          */
/* Function:     get_assign_dates()                                         */
/*                                                                          */
/* Description:  This function gets various dates from the database.        */
/*                                                                          */
/* SE               CO    Date      Description                             */
/* ---------------- ----- --------  ------------------------------------    */
/*                                                                          */
/****************************************************************************/
static void get_assign_dates(void)
{
    char FNC[] = "get_assign_dates()";
    int rc;
    struct mc_dte enroll_dte_str;
    struct mc_rtn_message rtn_msg_str;
    struct mc_pub_hlth_pgm hlth_pgm_str;


    /********************** KC19 Program Data ************************* */
    rc = c_get_pub_hlth_pgm(&hlth_pgm_str, &rtn_msg_str, 0, "KC19");
    check_rc(&rtn_msg_str, rc, FNC);
    sak_pub_hlth_kc19 = hlth_pgm_str.sak_pub_hlth;

    /********************** KC19 Program Data ************************* */
    rc = c_get_pub_hlth_pgm(&hlth_pgm_str, &rtn_msg_str, 0, "KC21");
    check_rc(&rtn_msg_str, rc, FNC);
    sak_pub_hlth_kc21 = hlth_pgm_str.sak_pub_hlth;

    rc = c_get_dte_enroll(&enroll_dte_str, &rtn_msg_str, sak_pub_hlth_kc19);
    check_rc(&rtn_msg_str, rc, FNC);
    dte_kc19_enroll = enroll_dte_str.dte;


    fprintf(stderr, "\n");
    fprintf(stderr, "Current date           = [ %d ]\n", dte_current);

    fprintf(err_rpt_file_ptr, "\n*************************************************************\n");
    fprintf(err_rpt_file_ptr, "  Autoassignment Log for Date [%d] 24 hr Time [%s]\n", dte_current, curr_time);
    fprintf(err_rpt_file_ptr, "*************************************************************\n");

    fprintf(stderr, "KC19/21 normal enrollment date = [ %d ]\n", dte_kc19_enroll);
    fprintf(stderr, "KC19/21 date of eligibility verification = [ %d ]\n", dte_kc_elig_verification);

    fprintf(err_rpt_file_ptr, "KC19/21 normal enrollment date = [ %d ]\n", dte_kc19_enroll);
    fprintf(err_rpt_file_ptr, "KC19/21 date of eligibility verification  = [ %d ]\n", dte_kc_elig_verification);
    fprintf(err_rpt_file_ptr, "KC21 date of max retro eligibility  = [ %d ]\n", dte_t21_max_retro);


    fprintf(stderr, "sak_pub_hlth_kc19 = [ %d ]\n", sak_pub_hlth_kc19);
    fprintf(stderr, "sak_pub_hlth_kc21 = [ %d ]\n", sak_pub_hlth_kc21);

}

/****************************************************************************/
/*                                                                          */
/* Function:    ConnectToDB()                                               */
/*                                                                          */
/* Description: Connect to the database.                                    */
/*                                                                          */
/****************************************************************************/
/*                 Modification Log:                                        */
/* Date     CO       SE         Description                                 */
/* -------- -------- ---------- ------------------------------------------- */
/*                                                                          */
/****************************************************************************/
static int ConnectToDB(void)
{
    int lConnectRtn;
    int lConnectRtn2;
    int lConnectRtn3;
    char FNC[] = "ConnectToDB()";

    lConnectRtn  = ConnectDB();
    lConnectRtn2 = ConnectDB2();
    lConnectRtn3 = ConnectDB3();

    if (lConnectRtn3 != ANSI_SQL_OK)
    {
        fprintf(stderr,"%s: ERROR- SQL DBCONN3 Connect Failed SQL Code [%8d]\n",
                FNC, lConnectRtn3);
        return FUNCTION_FAILED;
    }

    if (lConnectRtn2 != ANSI_SQL_OK)
    {
        fprintf(stderr,"%s: ERROR- SQL DBCONN2 Connect Failed SQL Code [%8d]\n",
                FNC, lConnectRtn2);
        return FUNCTION_FAILED;
    }

    if (lConnectRtn == ANSI_SQL_OK)
    {
        return FUNCTION_SUCCESSFUL;
    }
    else
    {
        fprintf(stderr,"%s: ERROR- SQL Connect Failed SQL Code [%8d]\n", FNC, lConnectRtn);
        return FUNCTION_FAILED;
    }
}

/****************************************************************************/
/*                                                                          */
/* Function:     get_next_sak()                                             */
/*                                                                          */
/* Description:  Retrieve the next system key to use for the                */
/*               specified sak_name and commit the changes                  */
/*                                                                          */
/*                                                                          */
/****************************************************************************/
static int get_next_sak(char parm_sak_name[18+1])
{
    EXEC SQL BEGIN DECLARE SECTION;
        char sql_sak_name[18+1];
        int sql_sak;
    EXEC SQL END DECLARE SECTION;

    strcpy(sql_sak_name, parm_sak_name);

    if (arg_save_to_db == mgd_TRUE)
    {
        EXEC SQL //AT DBCONN2
         SELECT sak
           INTO :sql_sak
           FROM t_system_keys
          WHERE sak_name = :sql_sak_name;
    }
    else
    {
        EXEC SQL SELECT  sak
                 INTO    :sql_sak
                 FROM    t_system_keys
                 WHERE   sak_name = :sql_sak_name;
    }


    sql_sak++;

    if (sqlca.sqlcode == 0)
    {
        EXEC SQL UPDATE t_system_keys
                    SET sak = :sql_sak
                  WHERE sak_name = :sql_sak_name;

        if (sqlca.sqlcode != 0)
        {
            fprintf(stderr, " DATABASE ERROR:\n"
                   "    Update of SAK value FAILED for "
                   "sak_name: [%s]\n", parm_sak_name);
            sqlerror();
            ind_abend = mgd_TRUE;
            finalize();
            exit(-1);
        }
        else
        {
//            if (arg_save_to_db == mgd_TRUE)
//            {
//                EXEC SQL AT DBCONN2 COMMIT;
//                if (sqlca.sqlcode != 0)
//                {
//                    fprintf(stderr, " DATABASE ERROR:\n"
//                           "    COMMIT of SAK value FAILED for "
//                           "sak_name: [%s]\n", parm_sak_name);
//                    sqlerror();
//                    ind_abend = mgd_TRUE;
//                    finalize();
//                    exit(-1);
//                }
//            }
        }
    }
    else
    {
        fprintf(stderr, " DATABASE ERROR:\n"
               "    Retrieve of SAK value FAILED for "
               "sak_name: [%s]\n", parm_sak_name);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(-1);
    }

    return(sql_sak);

}

/****************************************************************************/
/*                                                                          */
/* Function:     lock_recip_base()                                          */
/*                                                                          */
/* Description:  This function locks the beneficiary's base record          */
/*               while we are performing an insert of the PMP               */
/*               assignment so that Claims will always process with         */
/*               the most recent information.                               */
/*                                                                          */
/****************************************************************************/
static void lock_recip_base(void)
{
    EXEC SQL BEGIN DECLARE SECTION;
        int sql_sak_recip;
    EXEC SQL END DECLARE SECTION;

    EXEC SQL SELECT sak_recip
               INTO :sql_sak_recip
               FROM t_re_base
              WHERE sak_recip = :sql_pntl_rcd.sak_recip
         for UPDATE;

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf(stderr, " DATABASE ERROR:\n"
               "    SELECT for UPDATE in T_RE_BASE table FAILED for "
               "sak_recip [%d]\n", sql_pntl_rcd.sak_recip);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(-1);
    }
}

/****************************************************************************/
/*                                                                          */
/* Function:     update_pmp_panel_size()                                    */
/*                                                                          */
/* Description:  This function will increment the PMP's actual panel size.  */
/*                                                                          */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static void update_pmp_panel_size(int parm_sak_pmp_ser_loc)
{
    char FNC[] = "update_pmp_panel_size()";
    EXEC SQL BEGIN DECLARE SECTION;
        int sql_sak_pmp_ser_loc;
    EXEC SQL END DECLARE SECTION;

    sql_sak_pmp_ser_loc = parm_sak_pmp_ser_loc;

    EXEC SQL UPDATE t_pmp_svc_loc
                SET num_future_panel = num_future_panel + 1
              WHERE sak_pmp_ser_loc = :sql_sak_pmp_ser_loc;

    if (sqlca.sqlcode != 0)
    {
        fprintf(stderr, "%s - DATABASE ERROR:\n"
               "    Update to NUM_ACT_PANEL in T_PMP_SVC_LOC table FAILED for "
               "sak_pmp_ser_loc [%d]\n", FNC, sql_sak_pmp_ser_loc);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(-1);
    }
}

/****************************************************************************/
/*                                                                          */
/* Function:     get_case_pmp()                                             */
/*                                                                          */
/* Description: Attempts to retrieve a PMP that another member of           */
/*              the recip's case is currently assigned to.  It also         */
/*              verifies that the PMP is a good match for the recip         */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static int get_case_pmp(int *parm_sak_pub_hlth, int parm_dte_benefit_month)
{
    char FNC[] = "get_case_pmp()";
    EXEC SQL BEGIN DECLARE SECTION;
        int sql_sak_pub_hlth;
        int temp_sak_pmp_ser_loc = -1;
        int sql_case_sak_pmp_ser_loc = -1;
        int sql_case_sak_pub_hlth;
        int sql_case_sak_prov;
        int sql_case_sak_re_pmp_assign;
        int sql_dte_current = 0;
        int sql_tmp_dte_benefit_month = 0;
    EXEC SQL END DECLARE SECTION;

    int recip_sak_pmp_ser_loc = -1;
    sql_dte_current = dte_current;

    if (arg_run_level == DAILY)
    {
        sql_tmp_dte_benefit_month = parm_dte_benefit_month;
        fprintf(err_rpt_file_ptr, "\t\t\t\tDaily Case logic will attempt to find assignments effective at some point during the month of [%d].\n",
                sql_tmp_dte_benefit_month);
    }
    else
    {
        sql_tmp_dte_benefit_month = sql_dte_enroll_pgm;
        fprintf(err_rpt_file_ptr, "\t\t\t\tMonthly Case logic will attempt to find assignments effective at some point during the benefit month of [%d].\n",
                sql_tmp_dte_benefit_month);
    }



// If the case members are split between multiple KanCare plans and the current beneficiary
// is under the age of 14, and any other case members are also under the age of 14, then
// the beneficiary should be assigned to the same KanCare plan.  If the current beneficiary
// is 14 or over and there are any other case members 14 and over, then the beneficiary
// should be assigned to the same plan.  Otherwise, assign them to the same KanCare plan as
// the first member of the case.

// Determine how distinct MCOs the other case members are assigned to...

    if (c_do_calc_recip_age(recip_dte_birth, dte_current) < 14)
    {
        EXEC SQL
         SELECT case_sak_pmp_ser_loc,
                case_sak_pub_hlth,
                case_sak_prov,
                case_sak_re_pmp_assign
           INTO :sql_case_sak_pmp_ser_loc,
                :sql_case_sak_pub_hlth,
                :sql_case_sak_prov,
                :sql_case_sak_re_pmp_assign
           FROM (
             SELECT pmp.sak_pmp_ser_loc as case_sak_pmp_ser_loc,
                    pmp.sak_pub_hlth as case_sak_pub_hlth,
                    pmp.sak_prov as case_sak_prov,
                    assign.sak_re_pmp_assign as case_sak_re_pmp_assign
               FROM t_pmp_svc_loc pmp,
                    t_re_base base,
                    t_re_pmp_assign assign,
                    t_re_elig elig
              WHERE elig.sak_recip = assign.sak_recip
                AND elig.sak_pgm_elig = assign.sak_pgm_elig
                AND elig.sak_recip = base.sak_recip
                AND assign.sak_pmp_ser_loc = pmp.sak_pmp_ser_loc
                AND base.sak_case = :recip_sak_case
                AND (TRUNC((MONTHS_BETWEEN((LAST_DAY (TO_DATE(:sql_dte_current, 'YYYYMMDD'))),
                    (TO_DATE( base.dte_birth, 'YYYYMMDD'))) / 12), 0 )) < 14
                AND elig.cde_status1 <> 'H'
                //AND elig.dte_effective <= TO_CHAR(LAST_DAY(TO_DATE(:sql_tmp_dte_benefit_month, 'YYYYMMDD')), 'YYYYMMDD')
                AND elig.dte_end >= TO_CHAR(ADD_MONTHS(LAST_DAY(TO_DATE(:sql_tmp_dte_benefit_month, 'YYYYMMDD')) +1, -1), 'YYYYMMDD')
               // AND pmp.dte_effective <= TO_CHAR(LAST_DAY(TO_DATE(:sql_tmp_dte_benefit_month, 'YYYYMMDD')), 'YYYYMMDD')
                AND pmp.dte_end >= TO_CHAR(ADD_MONTHS(LAST_DAY(TO_DATE(:sql_tmp_dte_benefit_month, 'YYYYMMDD')) +1, -1), 'YYYYMMDD')
                AND pmp.sak_pub_hlth IN (:sak_pub_hlth_kc19, :sak_pub_hlth_kc21)
                ORDER BY elig.dte_end DESC)
           WHERE ROWNUM < 2;

        if ( (sqlca.sqlcode != ANSI_SQL_OK) && (sqlca.sqlcode != ANSI_SQL_NOTFOUND) )
        {
            fprintf (stderr, "   ERROR: could not run the < 14 case query!\n");
            fflush(stderr);
            sqlerror();
            ind_abend = mgd_TRUE;
            finalize();
            exit(FAILURE);
        }

        if (sqlca.sqlcode == ANSI_SQL_OK)
        {
            if (arg_run_level == DAILY)
                fprintf(err_rpt_file_ptr, "\t\t\t\tAssignment sak_re_pmp_assign [%d], sak_pub_hlth [%d] for sak_case [%d] < 14 found and \n\t\t\t\t- will be used to create new KC assignment.\n",
                        sql_case_sak_re_pmp_assign, sql_case_sak_pub_hlth, recip_sak_case);
            else
                fprintf(err_rpt_file_ptr, "\t\tAssignment sak_re_pmp_assign [%d], sak_pub_hlth [%d] for sak_case [%d] < 14 found and \n\t\t- will be used to create new KC assignment.\n",
                        sql_case_sak_re_pmp_assign, sql_case_sak_pub_hlth, recip_sak_case);

            // We've retrieved the KC MCO that a case member is enrolled with, so now we need to verify it's for the same program...
            if (sql_pntl_rcd.sak_pub_hlth == sql_case_sak_pub_hlth)
            {
                recip_sak_pmp_ser_loc = sql_case_sak_pmp_ser_loc;
                *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
            }
            else
            {
                // Go get the sak_pmp_ser_loc for the same MCO that's for the current beneficiary's program...
                EXEC SQL
                 SELECT sak_pmp_ser_loc
                   INTO :temp_sak_pmp_ser_loc
                   FROM t_pmp_svc_loc
                  WHERE sak_prov = :sql_case_sak_prov
                    AND sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
                    AND dte_effective <= :sql_tmp_dte_benefit_month
                    AND dte_end > :sql_tmp_dte_benefit_month ;

                if (sqlca.sqlcode != ANSI_SQL_OK)
                {
                    fprintf (stderr, "   ERROR: could not run the pmp_case query for sak_prov [%d], sak_pub_hlth [%d] that's "
                            "attempting to find the other service location for sak_pmp_ser_loc [%d]\n",
                            sql_case_sak_prov, sql_pntl_rcd.sak_pub_hlth, sql_case_sak_pmp_ser_loc);
                    sqlerror();
                    ind_abend = mgd_TRUE;
                    finalize();
                    exit(FAILURE);
                }
                else
                {
                    recip_sak_pmp_ser_loc = temp_sak_pmp_ser_loc;
                    *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
                }
            }
        }
        else
        {
            if (arg_run_level == DAILY)
                fprintf(err_rpt_file_ptr, "\t\t\t\t< 14 Assignment NOT FOUND for sak_case [%d].\n", recip_sak_case);
            else
                fprintf(err_rpt_file_ptr, "\t\t< 14 Assignment NOT FOUND for sak_case [%d].\n", recip_sak_case);
        }
    }

    // If the beneficiary was 14 or over, or no sibling assignments found, then just try to find any other case assignments...
    if (recip_sak_pmp_ser_loc <= 0)
    {
        if (arg_run_level == DAILY)
            fprintf(err_rpt_file_ptr, "\t\t\t\tEither beneficiary > 14 or no < 14 assignments found.  Finding any other case assignments...\n");
        else
            fprintf(err_rpt_file_ptr, "\t\tEither beneficiary > 14 or no < 14 assignments found.  Finding any other case assignments...\n");

        EXEC SQL
         SELECT case_sak_pmp_ser_loc,
                case_sak_pub_hlth,
                case_sak_prov,
                case_sak_re_pmp_assign
           INTO :sql_case_sak_pmp_ser_loc,
                :sql_case_sak_pub_hlth,
                :sql_case_sak_prov,
                :sql_case_sak_re_pmp_assign
           FROM (
             SELECT pmp.sak_pmp_ser_loc as case_sak_pmp_ser_loc,
                    pmp.sak_pub_hlth as case_sak_pub_hlth,
                    pmp.sak_prov as case_sak_prov,
                    assign.sak_re_pmp_assign as case_sak_re_pmp_assign
               FROM t_pmp_svc_loc pmp,
                    t_re_base base,
                    t_re_pmp_assign assign,
                    t_re_elig elig
              WHERE elig.sak_recip = assign.sak_recip
                AND elig.sak_pgm_elig = assign.sak_pgm_elig
                AND elig.sak_recip = base.sak_recip
                AND assign.sak_pmp_ser_loc = pmp.sak_pmp_ser_loc
                AND base.sak_case = :recip_sak_case
                AND elig.cde_status1 <> 'H'
                //AND elig.dte_effective <= TO_CHAR(LAST_DAY(TO_DATE(:sql_tmp_dte_benefit_month, 'YYYYMMDD')), 'YYYYMMDD')
                AND elig.dte_end >= TO_CHAR(ADD_MONTHS(LAST_DAY(TO_DATE(:sql_tmp_dte_benefit_month, 'YYYYMMDD')) +1, -1), 'YYYYMMDD')
                //AND pmp.dte_effective <= TO_CHAR(LAST_DAY(TO_DATE(:sql_tmp_dte_benefit_month, 'YYYYMMDD')), 'YYYYMMDD')
                AND pmp.dte_end >= TO_CHAR(ADD_MONTHS(LAST_DAY(TO_DATE(:sql_tmp_dte_benefit_month, 'YYYYMMDD')) +1, -1), 'YYYYMMDD')
                AND pmp.sak_pub_hlth IN (:sak_pub_hlth_kc19, :sak_pub_hlth_kc21)
                ORDER BY elig.dte_end DESC)
           WHERE ROWNUM < 2;

        if ( (sqlca.sqlcode != ANSI_SQL_OK) && (sqlca.sqlcode != ANSI_SQL_NOTFOUND) )
        {
            fprintf (stderr, "   ERROR: could not run the >= 14 case query!\n");
            fflush(stderr);
            sqlerror();
            ind_abend = mgd_TRUE;
            finalize();
            exit(FAILURE);
        }

        if (sqlca.sqlcode == ANSI_SQL_OK)
        {
            if (arg_run_level == DAILY)
                fprintf(err_rpt_file_ptr, "\t\t\t\tAssignment sak_re_pmp_assign [%d], sak_pub_hlth [%d] for sak_case [%d] found and \n\t\t\t\t- will be used to create new KC assignment.\n",
                        sql_case_sak_re_pmp_assign, sql_case_sak_pub_hlth, recip_sak_case);
            else
                fprintf(err_rpt_file_ptr, "\t\tAssignment sak_re_pmp_assign [%d], sak_pub_hlth [%d] for sak_case [%d] found and \n\t\t- will be used to create new KC assignment.\n",
                        sql_case_sak_re_pmp_assign, sql_case_sak_pub_hlth, recip_sak_case);

            // We've retrieved the KC MCO that a case member is enrolled with, so now we need to verify it's for the same program...
            if (sql_pntl_rcd.sak_pub_hlth == sql_case_sak_pub_hlth)
            {
                recip_sak_pmp_ser_loc = sql_case_sak_pmp_ser_loc;
                *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
            }
            else
            {
                // Go get the sak_pmp_ser_loc for the same MCO that's for the current beneficiary's program...
                EXEC SQL
                 SELECT sak_pmp_ser_loc
                   INTO :temp_sak_pmp_ser_loc
                   FROM t_pmp_svc_loc
                  WHERE sak_prov = :sql_case_sak_prov
                    AND sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
                    AND dte_effective <= :sql_tmp_dte_benefit_month
                    AND dte_end > :sql_tmp_dte_benefit_month ;

                if (sqlca.sqlcode != ANSI_SQL_OK)
                {
                    fprintf (stderr, "   ERROR: could not run the pmp_case query for sak_prov [%d], sak_pub_hlth [%d] that's "
                            "attempting to find the other service location for sak_pmp_ser_loc [%d]\n",
                            sql_case_sak_prov, sql_pntl_rcd.sak_pub_hlth, sql_case_sak_pmp_ser_loc);
                    sqlerror();
                    ind_abend = mgd_TRUE;
                    finalize();
                    exit(FAILURE);
                }
                else
                {
                    recip_sak_pmp_ser_loc = temp_sak_pmp_ser_loc;
                    *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
                }
            }
        }
        else
        {
            if (arg_run_level == DAILY)
                fprintf(err_rpt_file_ptr, "\t\t\t\tAssignment NOT FOUND for sak_case [%d].\n", recip_sak_case);
            else
                fprintf(err_rpt_file_ptr, "\t\tAssignment NOT FOUND for sak_case [%d].\n", recip_sak_case);
        }
    }


    return(recip_sak_pmp_ser_loc);

}

/****************************************************************************/
/*                                                                          */
/* Function:     get_mco_choice_pmp()                                       */
/*                                                                          */
/* Description:                                                             */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static int get_mco_choice_pmp(int *parm_sak_pub_hlth )
{
    char FNC[] = "get_mco_choice_pmp()";
    EXEC SQL BEGIN DECLARE SECTION;
        int sql_sak_pub_hlth;
        int temp_sak_pmp_ser_loc = -1;
        int sql_prev_sak_pmp_ser_loc = -1;
        int sql_prev_sak_pub_hlth;
        int sql_prev_sak_prov;
        int sql_prev_sak_re_pmp_assign;
    EXEC SQL END DECLARE SECTION;

    int recip_sak_pmp_ser_loc = -1;

    EXEC SQL
     SELECT sak_pmp_ser_loc
       INTO :temp_sak_pmp_ser_loc
       FROM (
         SELECT pmp.sak_pmp_ser_loc AS sak_pmp_ser_loc
           FROM t_mc_mco_choice chc, t_pmp_svc_loc pmp
          WHERE chc.sak_recip = :sql_pntl_rcd.sak_recip
            AND pmp.sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
            AND DECODE(chc.cde_mco_choice, :UNITED_PRAP_SQL, :UNITED_SAK_PROV_SQL, :SUNFLOWER_PRAP_SQL, :SUNFLOWER_SAK_PROV_SQL, :AMERIGROUP_PRAP_SQL, :AMERIGROUP_SAK_PROV_SQL) = pmp.sak_prov
            AND TO_CHAR(chc.dte_added, 'YYYYMMDDHH24MISS') > :sql_mco_choice_last_run_datetime
            ORDER BY dte_benefit_month DESC)
       WHERE ROWNUM < 2;

    if ( (sqlca.sqlcode != ANSI_SQL_OK) && (sqlca.sqlcode != ANSI_SQL_NOTFOUND) )
    {
        fprintf (stderr, "   ERROR: could not run the MCO Choice query!\n");
        fflush(stderr);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    if (sqlca.sqlcode == ANSI_SQL_OK)
    {
        if (arg_run_level == DAILY)
            fprintf(err_rpt_file_ptr, "\t\t\t\tMCO Choice [%d] found that was added after last run date/time of [%d] and \n\t\t\t\t- will be used to create new KC assignment.\n",
                    temp_sak_pmp_ser_loc, sql_mco_choice_last_run_time);
        else
            fprintf(err_rpt_file_ptr, "\t\tMCO Choice [%d] found that was added after last run date/time of [%d] and \n\t\t- will be used to create new KC assignment.\n",
                    temp_sak_pmp_ser_loc, sql_mco_choice_last_run_time);

        recip_sak_pmp_ser_loc = temp_sak_pmp_ser_loc;
        *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
    }
    else
    {
        if (arg_run_level == DAILY)
            fprintf(err_rpt_file_ptr, "\t\t\t\tMCO Choice NOT FOUND for sak_recip [%d].\n", sql_pntl_rcd.sak_recip);
        else
            fprintf(err_rpt_file_ptr, "\t\tMCO Choice NOT FOUND for sak_recip [%d].\n", sql_pntl_rcd.sak_recip);
    }

    return(recip_sak_pmp_ser_loc);

}

/****************************************************************************/
/*                                                                          */
/* Function:     get_mco_choice_count()                                     */
/*                                                                          */
/* Description:                                                             */
/*                                                                          */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static int get_mco_choice_count(void)
{
    char FNC[] = "get_mco_choice_count()";
    EXEC SQL BEGIN DECLARE SECTION;
        int sql_count;
    EXEC SQL END DECLARE SECTION;

    EXEC SQL
     SELECT COUNT(*)
       INTO :sql_count
       FROM t_mc_mco_choice chc, t_pmp_svc_loc pmp
      WHERE chc.sak_recip = :sql_pntl_rcd.sak_recip
        AND pmp.sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
        AND DECODE(chc.cde_mco_choice, :UNITED_PRAP_SQL, :UNITED_SAK_PROV_SQL, :SUNFLOWER_PRAP_SQL, :SUNFLOWER_SAK_PROV_SQL, :AMERIGROUP_PRAP_SQL, :AMERIGROUP_SAK_PROV_SQL) = pmp.sak_prov
        AND TO_CHAR(chc.dte_added, 'YYYYMMDDHH24MISS') > :sql_mco_choice_last_run_datetime
      ;

    if ( (sqlca.sqlcode != ANSI_SQL_OK) && (sqlca.sqlcode != ANSI_SQL_NOTFOUND) )
    {
        fprintf (stderr, "   ERROR: could not run the MCO Choice count query!\n");
        fflush(stderr);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    if (sql_count > 0)
    {
        if (arg_run_level == DAILY)
            fprintf(err_rpt_file_ptr, "\t\t\tMCO Choice found that was added after last run date/time of [%d].\n",
                    sql_mco_choice_last_run_time);
        else
            fprintf(err_rpt_file_ptr, "\tMCO Choice found that was added after last run date/time of [%d].\n",
                    sql_mco_choice_last_run_time);
    }
    else
    {
        if (arg_run_level == DAILY)
            fprintf(err_rpt_file_ptr, "\t\t\tMCO Choice NOT FOUND for sak_recip [%d].\n", sql_pntl_rcd.sak_recip);
        else
            fprintf(err_rpt_file_ptr, "\tMCO Choice NOT FOUND for sak_recip [%d].\n", sql_pntl_rcd.sak_recip);
    }

    return(sql_count);

}

/****************************************************************************/
/*                                                                          */
/* Function:     get_prev_kc_pmp()                                          */
/*                                                                          */
/* Description:                                                             */
/*                                                                          */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static int get_prev_kc_pmp(int *parm_sak_pub_hlth )
{
    char FNC[] = "get_prev_kc_pmp()";
    EXEC SQL BEGIN DECLARE SECTION;
        int sql_sak_pub_hlth;
        int temp_sak_pmp_ser_loc = -1;
        int sql_prev_sak_pmp_ser_loc = -1;
        int sql_prev_sak_pub_hlth;
        int sql_prev_sak_prov;
        int sql_prev_sak_re_pmp_assign;
    EXEC SQL END DECLARE SECTION;

    int recip_sak_pmp_ser_loc = -1;

    EXEC SQL
     SELECT prev_sak_pmp_ser_loc,
            prev_sak_pub_hlth,
            prev_sak_prov,
            prev_sak_re_pmp_assign
       INTO :sql_prev_sak_pmp_ser_loc,
            :sql_prev_sak_pub_hlth,
            :sql_prev_sak_prov,
            :sql_prev_sak_re_pmp_assign
       FROM (
         SELECT pmp.sak_pmp_ser_loc as prev_sak_pmp_ser_loc,
                pmp.sak_pub_hlth as prev_sak_pub_hlth,
                pmp.sak_prov as prev_sak_prov,
                assign.sak_re_pmp_assign as prev_sak_re_pmp_assign
           FROM t_pmp_svc_loc pmp,
                t_re_pmp_assign assign,
                t_re_elig elig
          WHERE elig.sak_recip = assign.sak_recip
            AND elig.sak_pgm_elig = assign.sak_pgm_elig
            AND elig.sak_recip = :sql_pntl_rcd.sak_recip
            AND assign.sak_pmp_ser_loc = pmp.sak_pmp_ser_loc
            AND assign.cde_rsn_mc_stop IN ('59', '60')
            AND elig.cde_status1 = 'H'
            AND elig.dte_effective <= :sql_dte_enroll_pgm
            AND elig.dte_end >= :sql_dte_enroll_pgm
            AND pmp.dte_effective <= :sql_dte_enroll_pgm
            AND pmp.dte_end > :sql_dte_enroll_pgm
            AND pmp.sak_pub_hlth IN (:sak_pub_hlth_kc19, :sak_pub_hlth_kc21)
            ORDER BY elig.dte_end DESC)
       WHERE ROWNUM < 2;

    if ( (sqlca.sqlcode != ANSI_SQL_OK) && (sqlca.sqlcode != ANSI_SQL_NOTFOUND) )
    {
        fprintf (stderr, "   ERROR: could not run the prev pmp query!\n");
        fflush(stderr);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    if (sqlca.sqlcode == ANSI_SQL_OK)
    {
        if (arg_run_level == DAILY)
        {
            fprintf(err_rpt_file_ptr, "\t\t\t\tHistoried KC assignment sak_re_pmp_assign [%d], sak_pub_hlth [%d] found and \n\t\t- will be used to create new KC assignment.\n",
                    sql_prev_sak_re_pmp_assign, sql_prev_sak_pub_hlth);
        }
        else
        {
            fprintf(err_rpt_file_ptr, "\t\tHistoried KC assignment sak_re_pmp_assign [%d], sak_pub_hlth [%d] found and \n\t\t- will be used to create new KC assignment.\n",
                    sql_prev_sak_re_pmp_assign, sql_prev_sak_pub_hlth);
        }

        // We've retrieved the KC MCO that they were previously enrolled with, so now we need to verify it's for the same program...
        if (sql_pntl_rcd.sak_pub_hlth == sql_prev_sak_pub_hlth)
        {
            recip_sak_pmp_ser_loc = sql_prev_sak_pmp_ser_loc;
            *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
        }
        else
        {
            // Go get the sak_pmp_ser_loc for the same MCO that's for the current beneficiary's program...
            EXEC SQL
             SELECT sak_pmp_ser_loc
               INTO :temp_sak_pmp_ser_loc
               FROM t_pmp_svc_loc
              WHERE sak_prov = :sql_prev_sak_prov
                AND sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
                AND dte_effective <= :sql_dte_enroll_pgm
                AND dte_end > :sql_dte_enroll_pgm ;

            if (sqlca.sqlcode != ANSI_SQL_OK)
            {
                fprintf (stderr, "   ERROR: could not run the prev pmp query for sak_prov [%d], sak_pub_hlth [%d] that's "
                        "attempting to find the other service location for sak_pmp_ser_loc [%d]\n",
                        sql_prev_sak_prov, sql_pntl_rcd.sak_pub_hlth, sql_prev_sak_pmp_ser_loc);
                sqlerror();
                ind_abend = mgd_TRUE;
                finalize();
                exit(FAILURE);
            }
            else
            {
                recip_sak_pmp_ser_loc = temp_sak_pmp_ser_loc;
                *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
            }
        }
    }
    else
    {
        if (arg_run_level == DAILY)
            fprintf(err_rpt_file_ptr, "\t\t\t\tHistoried KC assignment NOT FOUND for sak_recip [%d].\n", sql_pntl_rcd.sak_recip);
        else
            fprintf(err_rpt_file_ptr, "\t\tHistoried KC assignment NOT FOUND for sak_recip [%d].\n", sql_pntl_rcd.sak_recip);
    }

    return(recip_sak_pmp_ser_loc);

}

/****************************************************************************/
/*                                                                          */
/* Function:     get_future_kc_pmp()                                        */
/*                                                                          */
/* Description:                                                             */
/*                                                                          */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static int get_future_kc_pmp(int *parm_sak_pub_hlth )
{
    char FNC[] = "get_future_kc_pmp()";
    EXEC SQL BEGIN DECLARE SECTION;
        int sql_sak_pub_hlth;
        int temp_sak_pmp_ser_loc = -1;
        int sql_future_sak_pmp_ser_loc = -1;
        int sql_future_sak_pub_hlth;
        int sql_future_sak_prov;
        int sql_future_sak_re_pmp_assign;
    EXEC SQL END DECLARE SECTION;

    int recip_sak_pmp_ser_loc = -1;

    EXEC SQL
     SELECT future_sak_pmp_ser_loc,
            future_sak_pub_hlth,
            future_sak_prov,
            future_sak_re_pmp_assign
       INTO :sql_future_sak_pmp_ser_loc,
            :sql_future_sak_pub_hlth,
            :sql_future_sak_prov,
            :sql_future_sak_re_pmp_assign
       FROM (
         SELECT pmp.sak_pmp_ser_loc as future_sak_pmp_ser_loc,
                pmp.sak_pub_hlth as future_sak_pub_hlth,
                pmp.sak_prov as future_sak_prov,
                assign.sak_re_pmp_assign as future_sak_re_pmp_assign
           FROM t_pmp_svc_loc pmp,
                t_re_pmp_assign assign,
                t_re_elig elig
          WHERE elig.sak_recip = assign.sak_recip
            AND elig.sak_pgm_elig = assign.sak_pgm_elig
            AND elig.sak_recip = :sql_pntl_rcd.sak_recip
            AND assign.sak_pmp_ser_loc = pmp.sak_pmp_ser_loc
            //AND assign.cde_rsn_mc_stop IN ('59', '60')
            AND elig.cde_status1 = ' '
            AND elig.dte_effective > :sql_kc_elig.dte_benefit_month
            //AND elig.dte_end >=
            AND pmp.dte_effective <= :sql_kc_elig.dte_benefit_month
            AND pmp.dte_end > :sql_kc_elig.dte_benefit_month
            AND pmp.sak_pub_hlth IN (:sak_pub_hlth_kc19, :sak_pub_hlth_kc21)
            ORDER BY elig.dte_effective ASC)
       WHERE ROWNUM < 2;

    if ( (sqlca.sqlcode != ANSI_SQL_OK) && (sqlca.sqlcode != ANSI_SQL_NOTFOUND) )
    {
        fprintf (stderr, "   ERROR: could not run the future pmp query!\n");
        fflush(stderr);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    if (sqlca.sqlcode == ANSI_SQL_OK)
    {
        fprintf(err_rpt_file_ptr, "\t\t\t\tFuture KC assignment sak_re_pmp_assign [%d], sak_pub_hlth [%d] found and \n\t\t\t\t- will be used to create new KC assignment.\n",
                sql_future_sak_re_pmp_assign, sql_future_sak_pub_hlth);

        // We've retrieved the KC MCO that they were previously enrolled with, so now we need to verify it's for the same program...
        if (sql_pntl_rcd.sak_pub_hlth == sql_future_sak_pub_hlth)
        {
            recip_sak_pmp_ser_loc = sql_future_sak_pmp_ser_loc;
            *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
        }
        else
        {
            // Go get the sak_pmp_ser_loc for the same MCO that's for the current beneficiary's program...
            EXEC SQL
             SELECT sak_pmp_ser_loc
               INTO :temp_sak_pmp_ser_loc
               FROM t_pmp_svc_loc
              WHERE sak_prov = :sql_future_sak_prov
                AND sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
                AND dte_effective <= :sql_kc_elig.dte_benefit_month
                AND dte_end > :sql_kc_elig.dte_benefit_month ;

            if (sqlca.sqlcode != ANSI_SQL_OK)
            {
                fprintf (stderr, "   ERROR: could not run the future pmp query for sak_prov [%d], sak_pub_hlth [%d] that's "
                        "attempting to find the other service location for sak_pmp_ser_loc [%d]\n",
                        sql_future_sak_prov, sql_pntl_rcd.sak_pub_hlth, sql_future_sak_pmp_ser_loc);
                sqlerror();
                ind_abend = mgd_TRUE;
                finalize();
                exit(FAILURE);
            }
            else
            {
                recip_sak_pmp_ser_loc = temp_sak_pmp_ser_loc;
                *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
            }
        }
    }
    else
    {
        fprintf(err_rpt_file_ptr, "\t\t\t\tFuture KC assignment NOT FOUND for sak_recip [%d].\n", sql_pntl_rcd.sak_recip);
    }

    return(recip_sak_pmp_ser_loc);

}

/****************************************************************************/
/*                                                                          */
/* Function:     get_prev_kc_pmp_180_day()                                  */
/*                                                                          */
/* Description:                                                             */
/*                                                                          */
/*                                                                          */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static int get_prev_kc_pmp_180_day(int *parm_sak_pub_hlth )
{
    char FNC[] = "get_prev_kc_pmp_180_day()";
    EXEC SQL BEGIN DECLARE SECTION;
        int sql_sak_pub_hlth;
        int temp_sak_pmp_ser_loc = -1;
        int sql_prev_sak_pmp_ser_loc = -1;
        int sql_prev_sak_pub_hlth;
        int sql_prev_sak_prov;
        int sql_prev_sak_re_pmp_assign;
//        int sql_dte_current = 0;
    EXEC SQL END DECLARE SECTION;

    int recip_sak_pmp_ser_loc = -1;

//    sql_dte_current = dte_current;

    EXEC SQL
     SELECT prev_sak_pmp_ser_loc,
            prev_sak_pub_hlth,
            prev_sak_prov,
            prev_sak_re_pmp_assign
       INTO :sql_prev_sak_pmp_ser_loc,
            :sql_prev_sak_pub_hlth,
            :sql_prev_sak_prov,
            :sql_prev_sak_re_pmp_assign
       FROM (
         SELECT pmp.sak_pmp_ser_loc as prev_sak_pmp_ser_loc,
                pmp.sak_pub_hlth as prev_sak_pub_hlth,
                pmp.sak_prov as prev_sak_prov,
                assign.sak_re_pmp_assign as prev_sak_re_pmp_assign
           FROM t_pmp_svc_loc pmp,
                t_re_pmp_assign assign,
                t_re_elig elig
          WHERE elig.sak_recip = assign.sak_recip
            AND elig.sak_pgm_elig = assign.sak_pgm_elig
            AND elig.sak_recip = :sql_pntl_rcd.sak_recip
            AND assign.sak_pmp_ser_loc = pmp.sak_pmp_ser_loc
            //AND assign.cde_rsn_mc_stop IN ('59', '60')
            AND elig.cde_status1 = ' '
            //AND elig.dte_effective <= :sql_dte_enroll_pgm
            AND elig.dte_end >= :dte_current_minus_180
            AND pmp.dte_effective <= :sql_dte_enroll_pgm
            AND pmp.dte_end > :sql_dte_enroll_pgm
            AND pmp.sak_pub_hlth IN (:sak_pub_hlth_kc19, :sak_pub_hlth_kc21)
            ORDER BY elig.dte_end DESC)
       WHERE ROWNUM < 2;

    if ( (sqlca.sqlcode != ANSI_SQL_OK) && (sqlca.sqlcode != ANSI_SQL_NOTFOUND) )
    {
        fprintf (stderr, "   ERROR: could not run the prev pmp 180 day query!\n");
        fflush(stderr);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    if (sqlca.sqlcode == ANSI_SQL_OK)
    {
        if (arg_run_level == DAILY)
            fprintf(err_rpt_file_ptr, "\t\t\t\tNon-historied KC assignment ending on/after 180 days prior to\n"
                    "\t\t\t\t-   cycle date [%d] (sak_re_pmp_assign [%d], sak_pub_hlth [%d]) found and \n"
                    "\t\t\t\t-   will be used to create new KC assignment.\n",
                    dte_current_minus_180, sql_prev_sak_re_pmp_assign, sql_prev_sak_pub_hlth);
        else
            fprintf(err_rpt_file_ptr, "\t\tNon-historied KC assignment ending on/after 180 days prior to\n"
                    "\t\t-    cycle date [%d] (sak_re_pmp_assign [%d], sak_pub_hlth [%d]) found and \n"
                    "\t\t-    will be used to create new KC assignment.\n",
                    dte_current_minus_180, sql_prev_sak_re_pmp_assign, sql_prev_sak_pub_hlth);

        // We've retrieved the KC MCO that they were previously enrolled with, so now we need to verify it's for the same program...
        if (sql_pntl_rcd.sak_pub_hlth == sql_prev_sak_pub_hlth)
        {
            recip_sak_pmp_ser_loc = sql_prev_sak_pmp_ser_loc;
            *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
        }
        else
        {
            // Go get the sak_pmp_ser_loc for the same MCO that's for the current beneficiary's program...
            EXEC SQL
             SELECT sak_pmp_ser_loc
               INTO :temp_sak_pmp_ser_loc
               FROM t_pmp_svc_loc
              WHERE sak_prov = :sql_prev_sak_prov
                AND sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
                AND dte_effective <= :sql_dte_enroll_pgm
                AND dte_end > :sql_dte_enroll_pgm ;

            if (sqlca.sqlcode != ANSI_SQL_OK)
            {
                fprintf (stderr, "   ERROR: could not run the prev pmp 180 day query for sak_prov [%d], sak_pub_hlth [%d] that's "
                        "attempting to find the other service location for sak_pmp_ser_loc [%d]\n",
                        sql_prev_sak_prov, sql_pntl_rcd.sak_pub_hlth, sql_prev_sak_pmp_ser_loc);
                sqlerror();
                ind_abend = mgd_TRUE;
                finalize();
                exit(FAILURE);
            }
            else
            {
                recip_sak_pmp_ser_loc = temp_sak_pmp_ser_loc;
                *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
            }
        }
    }
    else
    {
        if (arg_run_level == DAILY)
            fprintf(err_rpt_file_ptr, "\t\t\t\tNon-historied 180 day KC assignment NOT FOUND for sak_recip [%d].\n", sql_pntl_rcd.sak_recip);
        else
            fprintf(err_rpt_file_ptr, "\t\tNon-historied 180 day KC assignment NOT FOUND for sak_recip [%d].\n", sql_pntl_rcd.sak_recip);
    }

    return(recip_sak_pmp_ser_loc);

}

/****************************************************************************/
/*                                                                          */
/* Function:     get_prev_kc_pmp_90_day()                                   */
/*                                                                          */
/* Description:                                                             */
/*                                                                          */
/*                                                                          */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static int get_prev_kc_pmp_90_day(int *parm_sak_pub_hlth )
{
    char FNC[] = "get_prev_kc_pmp_90_day()";
    EXEC SQL BEGIN DECLARE SECTION;
        int sql_sak_pub_hlth;
        int temp_sak_pmp_ser_loc = -1;
        int sql_prev_sak_pmp_ser_loc = -1;
        int sql_prev_sak_pub_hlth;
        int sql_prev_sak_prov;
        int sql_prev_sak_re_pmp_assign;
//        int sql_dte_current = 0;
    EXEC SQL END DECLARE SECTION;

    int recip_sak_pmp_ser_loc = -1;

//    sql_dte_current = dte_current;

    EXEC SQL
     SELECT prev_sak_pmp_ser_loc,
            prev_sak_pub_hlth,
            prev_sak_prov,
            prev_sak_re_pmp_assign
       INTO :sql_prev_sak_pmp_ser_loc,
            :sql_prev_sak_pub_hlth,
            :sql_prev_sak_prov,
            :sql_prev_sak_re_pmp_assign
       FROM (
         SELECT pmp.sak_pmp_ser_loc as prev_sak_pmp_ser_loc,
                pmp.sak_pub_hlth as prev_sak_pub_hlth,
                pmp.sak_prov as prev_sak_prov,
                assign.sak_re_pmp_assign as prev_sak_re_pmp_assign
           FROM t_pmp_svc_loc pmp,
                t_re_pmp_assign assign,
                t_re_elig elig
          WHERE elig.sak_recip = assign.sak_recip
            AND elig.sak_pgm_elig = assign.sak_pgm_elig
            AND elig.sak_recip = :sql_pntl_rcd.sak_recip
            AND assign.sak_pmp_ser_loc = pmp.sak_pmp_ser_loc
            //AND assign.cde_rsn_mc_stop IN ('59', '60')
            AND elig.cde_status1 = ' '
            //AND elig.dte_effective <= :sql_dte_enroll_pgm
            AND elig.dte_end >= :dte_current_minus_3_mnths
            AND pmp.dte_effective <= :sql_dte_enroll_pgm
            AND pmp.dte_end > :sql_dte_enroll_pgm
            AND pmp.sak_pub_hlth IN (:sak_pub_hlth_kc19, :sak_pub_hlth_kc21)
            ORDER BY elig.dte_end DESC)
       WHERE ROWNUM < 2;

    if ( (sqlca.sqlcode != ANSI_SQL_OK) && (sqlca.sqlcode != ANSI_SQL_NOTFOUND) )
    {
        fprintf (stderr, "   ERROR: could not run the prev pmp 90 day query!\n");
        fflush(stderr);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    if (sqlca.sqlcode == ANSI_SQL_OK)
    {
        if (arg_run_level == DAILY)
            fprintf(err_rpt_file_ptr, "\t\t\t\tNon-historied KC assignment ending on/after 3 months prior to cycle date [%d] (sak_re_pmp_assign [%d], "
                    "sak_pub_hlth [%d]) found and \n\t\t- will be used to create new KC assignment.\n",
                    dte_current_minus_3_mnths, sql_prev_sak_re_pmp_assign, sql_prev_sak_pub_hlth);
        else
            fprintf(err_rpt_file_ptr, "\t\tNon-historied KC assignment ending on/after 3 months prior to cycle date [%d] (sak_re_pmp_assign [%d], "
                    "sak_pub_hlth [%d]) found and \n\t\t- will be used to create new KC assignment.\n",
                    dte_current_minus_3_mnths, sql_prev_sak_re_pmp_assign, sql_prev_sak_pub_hlth);
        // We've retrieved the KC MCO that they were previously enrolled with, so now we need to verify it's for the same program...
        if (sql_pntl_rcd.sak_pub_hlth == sql_prev_sak_pub_hlth)
        {
            recip_sak_pmp_ser_loc = sql_prev_sak_pmp_ser_loc;
            *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
        }
        else
        {
            // Go get the sak_pmp_ser_loc for the same MCO that's for the current beneficiary's program...
            EXEC SQL
             SELECT sak_pmp_ser_loc
               INTO :temp_sak_pmp_ser_loc
               FROM t_pmp_svc_loc
              WHERE sak_prov = :sql_prev_sak_prov
                AND sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
                AND dte_effective <= :sql_dte_enroll_pgm
                AND dte_end > :sql_dte_enroll_pgm ;

            if (sqlca.sqlcode != ANSI_SQL_OK)
            {
                fprintf (stderr, "   ERROR: could not run the prev pmp 90 day query for sak_prov [%d], sak_pub_hlth [%d] that's "
                        "attempting to find the other service location for sak_pmp_ser_loc [%d]\n",
                        sql_prev_sak_prov, sql_pntl_rcd.sak_pub_hlth, sql_prev_sak_pmp_ser_loc);
                sqlerror();
                ind_abend = mgd_TRUE;
                finalize();
                exit(FAILURE);
            }
            else
            {
                recip_sak_pmp_ser_loc = temp_sak_pmp_ser_loc;
                *parm_sak_pub_hlth = sql_pntl_rcd.sak_pub_hlth;
            }
        }
    }
    else
    {
        if (arg_run_level == DAILY)
            fprintf(err_rpt_file_ptr, "\t\t\t\tNon-historied 90 day KC assignment NOT FOUND for sak_recip [%d].\n", sql_pntl_rcd.sak_recip);
        else
            fprintf(err_rpt_file_ptr, "\t\tNon-historied 90 day KC assignment NOT FOUND for sak_recip [%d].\n", sql_pntl_rcd.sak_recip);
    }

    return(recip_sak_pmp_ser_loc);

}

/****************************************************************************/
/*                                                                          */
/* Function:     get_morbidity_mco()                                        */
/*                                                                          */
/* Description: Attempts to                                                 */
/*                                                                          */
/*                                                                          */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static int get_morbidity_mco(int *parm_sak_pub_hlth )
{
    char FNC[] = "get_morbidity_mco()";

    EXEC SQL BEGIN DECLARE SECTION;
        int sql_kc_sak_pmp_ser_loc;
        int sql_kc_sak_pub_hlth;
        int sql_kc_sak_prov;
        int sql_kc_pmp_morbidity_count;
    EXEC SQL END DECLARE SECTION;

    int recip_sak_pmp_ser_loc = -1;

    EXEC SQL
     SELECT kc_sak_pmp_ser_loc,
            kc_sak_pub_hlth,
            kc_sak_prov,
            kc_pmp_morbidity_count
       INTO :sql_kc_sak_pmp_ser_loc,
            :sql_kc_sak_pub_hlth,
            :sql_kc_sak_prov,
            :sql_kc_pmp_morbidity_count
       FROM (
         SELECT kc_pmp.sak_pmp_ser_loc as kc_sak_pmp_ser_loc,
                kc_pmp.sak_pub_hlth    as kc_sak_pub_hlth,
                kc_pmp.sak_prov        as kc_sak_prov,
                pmpmo.num_count        as kc_pmp_morbidity_count
           FROM t_mc_pmp_morbidity pmpmo,
                t_pmp_svc_loc kc_pmp
          WHERE pmpmo.sak_pmp_ser_loc = kc_pmp.sak_pmp_ser_loc
            AND kc_pmp.sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
            AND pmpmo.cde_morbidity = :sql_pntl_rcd.cde_morbidity
            AND pmpmo.dte_morbidity = :sql_max_dte_morbidity
        ORDER BY pmpmo.num_count)
      WHERE ROWNUM < 2;

    if ((sqlca.sqlcode != ANSI_SQL_OK) && (sqlca.sqlcode != ANSI_SQL_NOTFOUND))
    {
        fprintf (stderr, "   ERROR: Could not execute morbidity query!\n");
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    if (sqlca.sqlcode == ANSI_SQL_OK)
    {
        if (arg_run_level == DAILY)
        {
            fprintf(err_rpt_file_ptr, "\t\t\t\tFound MCO through Morbidity Logic.\n\t\t\t\t\tKC MCO sak_pmp_ser_loc [%d], KC MCO sak_pub_hlth [%d], "
                "KC MCO sak_prov [%d], \n\t\t\t\t\t- KC MCO Morbidity count [%d] for cde_morbidity [%s]\n",
                sql_kc_sak_pmp_ser_loc, sql_kc_sak_pub_hlth,
                sql_kc_sak_prov, sql_kc_pmp_morbidity_count, sql_pntl_rcd.cde_morbidity);
        }
        else
        {
            fprintf(err_rpt_file_ptr, "\t\tFound MCO through Morbidity Logic.\n\t\t\tKC MCO sak_pmp_ser_loc [%d], KC MCO sak_pub_hlth [%d], "
                "KC MCO sak_prov [%d], \n\t\t\t- KC MCO Morbidity count [%d] for cde_morbidity [%s]\n",
                sql_kc_sak_pmp_ser_loc, sql_kc_sak_pub_hlth,
                sql_kc_sak_prov, sql_kc_pmp_morbidity_count, sql_pntl_rcd.cde_morbidity);
        }


        recip_sak_pmp_ser_loc = sql_kc_sak_pmp_ser_loc;
        *parm_sak_pub_hlth = sql_kc_sak_pub_hlth;
    }

    return(recip_sak_pmp_ser_loc);

}

/****************************************************************************/
/*                                                                          */
/* Function:     check_rc()                                                 */
/*                                                                          */
/* Description:  Check the return code form the common function             */
/*                                                                          */
/****************************************************************************/
static void check_rc(struct mc_rtn_message *rtn_msg_str_ptr, int parm_rc, char *FNC)
{
    if (parm_rc != FUNCTION_SUCCESSFUL)
    {
        c_do_display_rtn_msg(rtn_msg_str_ptr, FNC);
        ROLLBACK();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }
}

/****************************************************************************/
/*                                                                          */
/* Function:     sqlerror()                                                 */
/*                                                                          */
/* Description:  Displays troubleshooting information from Oracle           */
/*               when an error has occured.                                 */
/*                                                                          */
/****************************************************************************/
static void sqlerror(void)
{
    fflush(err_rpt_file_ptr);
    fprintf(stderr,"\n\nORACLE ERROR DETECTED:\n");

    sqlca.sqlerrm.sqlerrmc[sqlca.sqlerrm.sqlerrml] = '\0';
    fprintf(stderr, "\tOracle Error Number [%d]\n", sqlca.sqlcode);
    fprintf(stderr, "\tOracle Error Message: [%s]\n", sqlca.sqlerrm.sqlerrmc);

    fflush(stderr);
    ROLLBACK();
}

/****************************************************************************/
/*                                                                          */
/* Function:     save_to_db()                                               */
/*                                                                          */
/* Description:  Commits the changes to the database                        */
/*                                                                          */
/****************************************************************************/
static void save_to_db(char FNC[30])
{
    char tmp_type[10];

    /* COMMIT or ROLLBACK the changes depending on the command line parm... */
    if (arg_save_to_db == mgd_TRUE)
    {
        strcpy(tmp_type, "COMMIT");
        EXEC SQL COMMIT;
    }
    else
    {
        strcpy(tmp_type, "ROLLBACK");

        /* A rollback should NOT be performed before the end of */
        /* processing because we want it to act exactly like it */
        /* would if the processing had actually been made for   */
        /* each potential record.                               */
        /* EXEC SQL ROLLBACK; */
        sqlca.sqlcode = 0;
    }

    if (sqlca.sqlcode != 0)
    {
        fprintf(stderr, "DATABASE ERROR in %s:  %s FAILED for "
               "sak_recip [%d], sak_pub_hlth [%d]\n", FNC, tmp_type, sql_pntl_rcd.sak_recip,
               sql_pntl_rcd.sak_pub_hlth);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }
}

/****************************************************************************/
/*                                                                          */
/* Function:     rollback()                                                 */
/*                                                                          */
/* Description:  Rolls back the database changes.                           */
/*                                                                          */
/****************************************************************************/
static void ROLLBACK(void)
{
    fprintf(stderr, "\n\nRolling back any database updates...\n");
    EXEC SQL ROLLBACK WORK RELEASE;

    if (sqlca.sqlcode != 0)
        fprintf(stderr, "\tRollback FAILED - SQL ERROR CODE [%d]\n\n", sqlca.sqlcode);
    else
        fprintf(stderr, "\tRollback successful.\n\n");

    fflush(stderr);
}

/****************************************************************************/
/*                                                                          */
/* Function:     assign_case_pmp()                                          */
/*                                                                          */
/* Description:  Try to assign the PMP found in bene's case.                */
/*                                                                          */
/* Date       CO     SE             Description                             */
/* ---------- ------ -------------- --------------------------------------  */
/*                                                                          */
/****************************************************************************/
static void assign_case_pmp()
{
    char FNC[] = "assign_case_pmp()";
    int tmp_sak_pmp_ser_loc = 0;
    int tmp_sak_pub_hlth = 0;
    int tmp_dte_benefit_month_dummy = 0;

    /* Retrieve the recip's previous PMP for the HW19 or HCK program */
    tmp_sak_pmp_ser_loc = get_case_pmp(&tmp_sak_pub_hlth, tmp_dte_benefit_month_dummy);

    if ( tmp_sak_pmp_ser_loc > 0 )
    {
        make_pmp_assgn(tmp_sak_pmp_ser_loc, START_RSN_86_CASE_CONTINUITY,
                      (tmp_sak_pub_hlth == sak_pub_hlth_kc21) ? SAK_ENT_AUTOASSIGN_CASE_KC21 : SAK_ENT_AUTOASSIGN_CASE_KC19,
                      sql_dte_enroll_pgm, tmp_sak_pub_hlth, 0, " ");
        case_logic_cnt++;
    }
}

/****************************************************************************/
/*                                                                          */
/* Function:     assign_prev_kc_pmp()                                       */
/*                                                                          */
/* Description:  Try to assign to the KC MCO that the beneficiary was       */
/*               (possibly) previously enrolled with before getting their   */
/*               elig changed from TXIX to TXXI or vice versa.              */
/*                                                                          */
/* Date       CO     SE             Description                             */
/* ---------- ------ -------------- --------------------------------------  */
/*                                                                          */
/****************************************************************************/
static void assign_prev_kc_pmp(void)
{
    char FNC[] = "assign_prev_kc_pmp()";
    int tmp_sak_pmp_ser_loc = 0;
    int tmp_sak_pub_hlth = 0;

    /* Retrieve the recip's previous PMP for the HW19 or HCK program */
    tmp_sak_pmp_ser_loc = get_prev_kc_pmp(&tmp_sak_pub_hlth);

    if ( tmp_sak_pmp_ser_loc > 0 )
    {
        make_pmp_assgn(tmp_sak_pmp_ser_loc, START_RSN_96_CONTINUITY_OF_PLAN,
                      (tmp_sak_pub_hlth == sak_pub_hlth_kc21) ? SAK_ENT_AUTOASSIGN_PREV_KC_PMP_PGM_CHG_KC21 : SAK_ENT_AUTOASSIGN_PREV_KC_PMP_PGM_CHG_KC19,
                      sql_dte_enroll_pgm, tmp_sak_pub_hlth, 0, " ");
           pmp_chg_cnt++;
    }
}

///****************************************************************************/
///*                                                                          */
///* Function:     assign_future_kc_pmp()                                     */
///*                                                                          */
///* Description:  Try to assign to the KC MCO that the beneficiary was       */
///*               (possibly) previously enrolled with before getting their   */
///*               elig changed from TXIX to TXXI or vice versa.              */
///*                                                                          */
///* Date       CO     SE             Description                             */
///* ---------- ------ -------------- --------------------------------------  */
///*                                                                          */
///****************************************************************************/
//static void assign_future_kc_pmp(void)
//{
//    char FNC[] = "assign_future_kc_pmp()";
//    int tmp_sak_pmp_ser_loc = 0;
//    int tmp_sak_pub_hlth = 0;
//
//    /* Retrieve the recip's previous PMP for the HW19 or HCK program */
//    tmp_sak_pmp_ser_loc = get_future_kc_pmp(&tmp_sak_pub_hlth);
//
//    if ( tmp_sak_pmp_ser_loc > 0 )
//    {
//        // Override the end date logic that's in c_do_create_pmp_assign_2...
//        c_set_asgn_dte_end_override(c_get_month_end(sql_kc_elig.dte_benefit_month));
//
//        make_pmp_assgn(tmp_sak_pmp_ser_loc, START_RSN_96_CONTINUITY_OF_PLAN,
//                      (tmp_sak_pub_hlth == sak_pub_hlth_kc21) ? SAK_ENT_AUTOASSIGN_PREV_KC_PMP_PGM_CHG_KC21 : SAK_ENT_AUTOASSIGN_PREV_KC_PMP_PGM_CHG_KC19,
//                      sql_kc_elig.dte_benefit_month, tmp_sak_pub_hlth, 0, " ");
//        //pmp_chg_cnt++;
//    }
//}

/****************************************************************************/
/*                                                                          */
/* Function:     assign_mco_choice()                                        */
/*                                                                          */
/* Description:  Try to assign to the KC MCO that the beneficiary was       */
/*               (possibly) previously enrolled with before getting their   */
/*               elig changed from TXIX to TXXI or vice versa.              */
/*                                                                          */
/* Date       CO     SE             Description                             */
/* ---------- ------ -------------- --------------------------------------  */
/*                                                                          */
/****************************************************************************/
static void assign_mco_choice(void)
{
    char FNC[] = "assign_mco_choice()";
    int tmp_sak_pmp_ser_loc = 0;
    int tmp_sak_pub_hlth = 0;

    /* Retrieve the recip's previous PMP for the HW19 or HCK program */
    tmp_sak_pmp_ser_loc = get_mco_choice_pmp(&tmp_sak_pub_hlth);

    if ( tmp_sak_pmp_ser_loc > 0 )
    {
        make_pmp_assgn(tmp_sak_pmp_ser_loc, START_RSN_A1_CHOICE_VIA_MEDICAID_APP,
                      (tmp_sak_pub_hlth == sak_pub_hlth_kc21) ? SAK_ENT_AUTOASSIGN_CHOICE_KC21 : SAK_ENT_AUTOASSIGN_CHOICE_KC19,
                      sql_dte_enroll_pgm, tmp_sak_pub_hlth, 0, " ");

        mco_choice_cnt++;
    }
}

/****************************************************************************/
/*                                                                          */
/* Function:     assign_prev_kc_pmp_180_day()                               */
/*                                                                          */
/* Description:  Try to assign to the KC MCO that the beneficiary was       */
/*               (possibly) previously enrolled with before getting their   */
/*               elig changed from TXIX to TXXI or vice versa.              */
/*                                                                          */
/* Date       CO     SE             Description                             */
/* ---------- ------ -------------- --------------------------------------  */
/*                                                                          */
/****************************************************************************/
static void assign_prev_kc_pmp_180_day(void)
{
    char FNC[] = "assign_prev_kc_pmp_180_day()";
    int tmp_sak_pmp_ser_loc = 0;
    int tmp_sak_pub_hlth = 0;
    char tmp_start_rsn[2+1];

    EXEC SQL BEGIN DECLARE SECTION;
        int sql_count;
    EXEC SQL END DECLARE SECTION;

    /* Retrieve the recip's previous PMP for the HW19 or HCK program */
    tmp_sak_pmp_ser_loc = get_prev_kc_pmp_180_day(&tmp_sak_pub_hlth);

    if ( tmp_sak_pmp_ser_loc > 0 )
    {
        // See if the previous assignment for the beneficiary was ended due to elig loss and ended the day prior to the current benefit month...
        EXEC SQL
         SELECT count(*)
           INTO :sql_count
           FROM T_RE_ELIG elig, t_re_pmp_assign asgn
          WHERE elig.sak_recip = :sql_pntl_rcd.sak_recip
            AND elig.sak_pub_hlth IN (SELECT sak_pub_hlth
                                        FROM t_pub_hlth_pgm
                                       WHERE cde_pgm_health IN ('KC19', 'KC21'))
            AND elig.sak_recip = asgn.sak_recip
            AND elig.sak_pgm_elig = asgn.sak_pgm_elig
            AND asgn.cde_rsn_mc_stop in ('58', '59', '60')
            AND elig.dte_end = TO_CHAR(last_day(ADD_MONTHS(TO_DATE(:dte_current_benefit_month,'YYYYMMDD'),-1)),'YYYYMMDD')
            AND elig.cde_status1 <> 'H';

        if (sqlca.sqlcode != ANSI_SQL_OK)
        {
            fprintf (stderr, "%s - ERROR: Could not count KanCare t_re_pmp_assign records ('58', '59', '60') for sak_recip [%d], dte_current_benefit_month [%d]!\n",
                FNC, sql_pntl_rcd.sak_recip, dte_current_benefit_month);
            sqlerror();
            exit(FAILURE);
        }

        if (sql_count > 0)
        {
            if (after_month_end == mgd_TRUE)

            fprintf(err_rpt_file_ptr, "\t\tPrevious PMP found was for different program - setting start reason to 'Default - Continuity of Plan'.\n");
            strcpy(tmp_start_rsn, START_RSN_96_CONTINUITY_OF_PLAN);
        }
        else
        {
            fprintf(err_rpt_file_ptr, "\t\tPrevious PMP found was for same program - setting start reason to 'KanCare Default - 180 Day reattachment'.\n");
            strcpy(tmp_start_rsn, START_RSN_94_PREVIOUS_ASSIGNMENT);
        }


        make_pmp_assgn(tmp_sak_pmp_ser_loc, tmp_start_rsn,
                      SAK_ENT_AUTOASSIGN_PREV_KC_PMP_180_DAY,
                      sql_dte_enroll_pgm, tmp_sak_pub_hlth, 0, " ");
        prev_pmp_180_cnt++;
    }
}

/****************************************************************************/
/*                                                                          */
/* Function:     assign_prev_kc_pmp_90_day()                                */
/*                                                                          */
/* Description:  Try to assign to the KC MCO that the beneficiary was       */
/*               (possibly) previously enrolled with before getting their   */
/*               elig changed from TXIX to TXXI or vice versa.              */
/*                                                                          */
/* Date       CO     SE             Description                             */
/* ---------- ------ -------------- --------------------------------------  */
/*                                                                          */
/****************************************************************************/
static void assign_prev_kc_pmp_90_day(void)
{
    char FNC[] = "assign_prev_kc_pmp_90_day()";
    int tmp_sak_pmp_ser_loc = 0;
    int tmp_sak_pub_hlth = 0;

    /* Retrieve the recip's previous PMP for the HW19 or HCK program */
    tmp_sak_pmp_ser_loc = get_prev_kc_pmp_90_day(&tmp_sak_pub_hlth);

    if ( tmp_sak_pmp_ser_loc > 0 )
    {
        // Override the end date logic that's in c_do_create_pmp_assign_2...
        if (arg_run_level == DAILY)
        {
            c_set_asgn_dte_end_override(c_get_month_end(sql_kc_elig.dte_benefit_month));

            make_pmp_assgn(tmp_sak_pmp_ser_loc, START_RSN_94_PREVIOUS_ASSIGNMENT,
                          SAK_ENT_AUTOASSIGN_PREV_KC_PMP_180_DAY,
                          sql_kc_elig.dte_benefit_month, tmp_sak_pub_hlth, 0, " ");
        }
        else
        {
            make_pmp_assgn(tmp_sak_pmp_ser_loc, START_RSN_94_PREVIOUS_ASSIGNMENT,
                          SAK_ENT_AUTOASSIGN_PREV_KC_PMP_180_DAY,
                          sql_dte_enroll_pgm, tmp_sak_pub_hlth, 0, " ");
        }

        pmp_180_cnt++;
    }
}

/****************************************************************************/
/*                                                                          */
/* Function:     assign_morbidity()                                         */
/*                                                                          */
/* Description:  Try to assign                                              */
/*                                                                          */
/* Date       CO     SE             Description                             */
/* ---------- ------ -------------- --------------------------------------  */
/*                                                                          */
/****************************************************************************/
static void assign_morbidity()
{
    char FNC[] = "assign_hw_mco_pcp()";
    int tmp_sak_pmp_ser_loc = 0;
    int tmp_sak_pub_hlth = 0;

    tmp_sak_pmp_ser_loc = get_morbidity_mco(&tmp_sak_pub_hlth);

    if ( tmp_sak_pmp_ser_loc > 0 )
    {

        make_pmp_assgn(tmp_sak_pmp_ser_loc, START_RSN_92_MORBIDITY,
                      (sql_pntl_rcd.sak_pub_hlth == sak_pub_hlth_kc19) ? SAK_ENT_AUTOASSIGN_MORBIDITY_KC19 : SAK_ENT_AUTOASSIGN_MORBIDITY_KC21,
                      sql_dte_enroll_pgm, sql_pntl_rcd.sak_pub_hlth, 0, " ");


        morbidity_cnt++;
    }
}

/****************************************************************************/
/*                                                                          */
/* Function:     make_pmp_assgn()                                           */
/*                                                                          */
/* Description:  Makes a PMP assignment for the current beneficiary.        */
/*                                                                          */
/****************************************************************************/
static int make_pmp_assgn(int parm_pmp_svc_loc, char *parm_assgn_rsn,
                          int parm_ent_autoassign, int parm_assgn_dte,
                          int parm_sak_pub_hlth, int parm_sak_prov_mbr,
                          char *parm_cde_svc_loc_mbr )
{
    char FNC[] = "make_pmp_assgn()";
    int sak_aid_elig;
    struct mc_rtn_message rtn_msg_str;
    struct mc_is_reason pmp_rsn_str;
    int rc_cfunc;

    EXEC SQL BEGIN DECLARE SECTION;
       char sql_tmp_stop_rsn[] = "  ";
       int sql_mc_ent_del = -1;
       int sql_parm_sak_pmp_ser_loc;
       int sql_count;
       int sql_tmp_sak_cde_aid;
       int sql_chc_sak_short;
       char sql_cde_assignment_action[1+1];
//       int sql_ltr_sak_re_pmp_assign = 0;
//       int sql_ltr_asgn_cnt = 0;
    EXEC SQL END DECLARE SECTION;

    sql_parm_sak_pmp_ser_loc = parm_pmp_svc_loc;

    /* Retrieve/Update the next SAK_RE_PMP_ASSIGN... */
    sak_re_pmp_assign = get_next_sak("SAK_RE_PMP_ASSIGN");
//    sql_ltr_sak_re_pmp_assign = sak_re_pmp_assign;
//    sql_ltr_asgn_cnt = rcd_cnt;

    /* Retrieve/Update the next SAK_AID_ELIG... */
    sak_aid_elig = get_next_sak("SAK_AID_ELIG");

    /* Lock the beneficiary's base record whil we do the update (the commit will unlock it)... */
    //lock_recip_base();

    if (recip_sak_cde_aid <= 0)
    {
        EXEC SQL SELECT aid_elig.sak_cde_aid
                   INTO :sql_tmp_sak_cde_aid
                   FROM T_RE_ELIG elig,
                        T_RE_AID_ELIG aid_elig,
                        T_PUB_HLTH_AID pgm_aid,
                        T_PGM_DEPENDENCY dep
                  WHERE aid_elig.sak_recip = elig.sak_recip AND
                        aid_elig.sak_pgm_elig = elig.sak_pgm_elig AND
                        aid_elig.sak_cde_aid = pgm_aid.sak_cde_aid AND
                        pgm_aid.sak_pub_hlth = dep.sak_child_pgm AND
                        elig.sak_pub_hlth = dep.sak_pub_hlth AND
                        dep.sak_child_pgm = :sql_pntl_rcd.sak_pub_hlth AND
                        aid_elig.sak_recip = :sql_pntl_rcd.sak_recip AND
                        aid_elig.dte_effective <= TO_CHAR(last_day( TO_DATE(:dte_kc_elig_verification,'YYYYMMDD') ),'YYYYMMDD') AND
                        aid_elig.dte_end >= :dte_kc_elig_verification AND
                        elig.dte_effective <= TO_CHAR(last_day( TO_DATE(:dte_kc_elig_verification,'YYYYMMDD') ),'YYYYMMDD') AND
                        elig.dte_end >= :dte_kc_elig_verification AND
                        elig.cde_status1 <> 'H' AND
                        aid_elig.cde_status1 <> 'H' AND
                        ROWNUM < 2;

        if ((sqlca.sqlcode != ANSI_SQL_OK) && (sqlca.sqlcode != ANSI_SQL_NOTFOUND))
        {
            fprintf (stderr, "%s - ERROR: Attempting to determine recipient's sak_cde_aid "
                    "for sak_recip [%d], sak_pub_hlth [%d] on date [%d]\n", FNC,
                    sql_pntl_rcd.sak_recip, sql_pntl_rcd.sak_pub_hlth, dte_kc_elig_verification);
            sqlerror();
            ind_abend = mgd_TRUE;
            finalize();
            exit(FAILURE);
        }
        else
        {
            if (sqlca.sqlcode == ANSI_SQL_NOTFOUND)
            {
                EXEC SQL
                 SELECT sak_cde_aid
                  INTO :sql_tmp_sak_cde_aid
                  FROM (
                         SELECT aid_elig.sak_cde_aid AS sak_cde_aid

                           FROM T_RE_ELIG elig,
                                T_RE_AID_ELIG aid_elig,
                                T_PUB_HLTH_AID pgm_aid,
                                T_PGM_DEPENDENCY dep
                          WHERE aid_elig.sak_recip = elig.sak_recip AND
                                aid_elig.sak_pgm_elig = elig.sak_pgm_elig AND
                                aid_elig.sak_cde_aid = pgm_aid.sak_cde_aid AND
                                pgm_aid.sak_pub_hlth = dep.sak_child_pgm AND
                                elig.sak_pub_hlth = dep.sak_pub_hlth AND
                                dep.sak_child_pgm = :sql_pntl_rcd.sak_pub_hlth AND
                                aid_elig.sak_recip = :sql_pntl_rcd.sak_recip AND
                                elig.cde_status1 <> 'H' AND
                                aid_elig.cde_status1 <> 'H'
                         ORDER BY aid_elig.dte_end DESC )
                      WHERE ROWNUM < 2;

                if ((sqlca.sqlcode != ANSI_SQL_OK) && (sqlca.sqlcode != ANSI_SQL_NOTFOUND))
                {
                    fprintf (stderr, "%s - ERROR: Attempting to determine recipient's sak_cde_aid "
                            "for sak_recip [%d], sak_pub_hlth [%d] on date [%d]\n", FNC,
                            sql_pntl_rcd.sak_recip, sql_pntl_rcd.sak_pub_hlth, dte_kc_elig_verification);
                    sqlerror();
                    ind_abend = mgd_TRUE;
                    finalize();
                    exit(FAILURE);
                }
                else
                {
                    if (sqlca.sqlcode == ANSI_SQL_NOTFOUND)
                    {
                        fprintf (stderr, "%s - Unable to determine recipient's sak_cde_aid "
                                "for sak_recip [%d], sak_pub_hlth [%d] on date [%d].  Using default value of zero instead.\n", FNC,
                                sql_pntl_rcd.sak_recip, sql_pntl_rcd.sak_pub_hlth, dte_kc_elig_verification);
                        sql_tmp_sak_cde_aid = 0;
                        if (arg_run_level == DAILY)
                        {
                            fprintf(err_rpt_file_ptr, "\t\t\t\tWas unable to determine recipient's sak_cde_aid and will be use default value of zero instead.\n");
                        }
                        else
                        {
                            fprintf(err_rpt_file_ptr, "\t\tWas unable to determine recipient's sak_cde_aid and will be use default value of zero instead.\n");
                        }
                    }
                }
            }
        }
    }
    else
    {
        sql_tmp_sak_cde_aid = recip_sak_cde_aid;
    }

    // When using Default Continuity of Plan as the start reason, would it be possible to add a check to see if the
    // stop reason the previous assignment is equal to 60?  If the stop reason isn't equal to 60, the start reason
    // should be Retro 90 Reattach [93].  This only applies to the daily assignments.
    if ( (strcmp(parm_assgn_rsn, START_RSN_96_CONTINUITY_OF_PLAN) == 0) && (arg_run_level == DAILY) )
    {
        EXEC SQL
         SELECT stop_rsn
           INTO :sql_tmp_stop_rsn
           FROM (
             SELECT asgn.cde_rsn_mc_stop AS stop_rsn
               FROM T_RE_ELIG elig, t_re_pmp_assign asgn
              WHERE elig.sak_recip = :sql_pntl_rcd.sak_recip
                AND elig.sak_pub_hlth IN (SELECT sak_pub_hlth
                                            FROM t_pub_hlth_pgm
                                           WHERE cde_pgm_health IN ('KC19', 'KC21'))
                AND elig.sak_recip = asgn.sak_recip
                AND elig.sak_pgm_elig = asgn.sak_pgm_elig
                AND elig.cde_status1 <> 'H'
                ORDER BY elig.dte_end DESC)
          WHERE rownum < 2;

        if (sqlca.sqlcode != ANSI_SQL_OK)
        {
            fprintf (stderr, "%s - ERROR: Could not retrieve previous assignment for sak_recip [%d]!\n", FNC, sql_pntl_rcd.sak_recip);
            sqlerror();
            exit(FAILURE);
        }

        if (strcmp(sql_tmp_stop_rsn, "60") != 0)
        {
            strcpy(parm_assgn_rsn, START_RSN_93_90_DAY_RETRO_REATTACH);
            fprintf(err_rpt_file_ptr, "\t\t\t\t\tCurrent start reason is Continuity of Plan, but previous stop reason was not '60' - changed start reason to 90 Day Retro Reattach [%s].\n",
                parm_assgn_rsn);
        }
    }

    if (arg_run_level == DAILY)
        fprintf(err_rpt_file_ptr, "\t\t\t\t\tCreating assignment for sak_pub_hlth [%d] on [%d] through [%d] with start reason [%s]\n",
            parm_sak_pub_hlth,  parm_assgn_dte, c_get_asgn_dte_end_override(), parm_assgn_rsn);
    else
        fprintf(err_rpt_file_ptr, "\t\t\tCreating assignment for sak_pub_hlth [%d] to sak_pmp_ser_loc [%d] on [%d] with start reason [%s]\n",
            parm_sak_pub_hlth,  parm_pmp_svc_loc, parm_assgn_dte, parm_assgn_rsn);

    /* Note: recip_sak_cde_aid is set by c_is_mc_eligible_2 in check_pgm_elig.  This needs to be*/
    /*  called prior to making assignment to current plan.*/
    rc_cfunc = c_do_create_pmp_assign_2(&rtn_msg_str, sak_re_pmp_assign, sql_pntl_rcd.sak_recip,
           parm_sak_pub_hlth, sak_aid_elig, sql_tmp_sak_cde_aid, parm_assgn_dte,
           parm_pmp_svc_loc, parm_sak_prov_mbr, parm_cde_svc_loc_mbr, parm_assgn_rsn, parm_ent_autoassign,
           parm_assgn_dte, mc_sak_elig_stop);

    check_rc(&rtn_msg_str, rc_cfunc, FNC);


    if (strcmp(parm_assgn_rsn, START_RSN_86_CASE_CONTINUITY) == 0)
        cnt_start_rsn_86++;
    else if (strcmp(parm_assgn_rsn, START_RSN_92_MORBIDITY) == 0)
        cnt_start_rsn_92++;
    else if (strcmp(parm_assgn_rsn, START_RSN_93_90_DAY_RETRO_REATTACH) == 0)
        cnt_start_rsn_93++;
    else if (strcmp(parm_assgn_rsn, START_RSN_94_PREVIOUS_ASSIGNMENT) == 0)
        cnt_start_rsn_94++;
    else if (strcmp(parm_assgn_rsn, START_RSN_96_CONTINUITY_OF_PLAN) == 0)
        cnt_start_rsn_96++;
    else if (strcmp(parm_assgn_rsn, START_RSN_97_RETRO_ASSIGNMENT) == 0)
        cnt_start_rsn_97++;
    else if (strcmp(parm_assgn_rsn, START_RSN_A1_CHOICE_VIA_MEDICAID_APP) == 0)
        cnt_start_rsn_A1++;
    else
        cnt_start_rsn_undefined++;

    display_pmp_info(parm_pmp_svc_loc, parm_assgn_dte);


    // Update the count for that MCO and morbidity rating...
    EXEC SQL
     UPDATE t_mc_pmp_morbidity
        SET num_count = num_count + 1
      WHERE sak_pmp_ser_loc = :sql_parm_sak_pmp_ser_loc
        AND cde_morbidity = :sql_pntl_rcd.cde_morbidity
        AND dte_morbidity = :sql_max_dte_morbidity;

    if ((sqlca.sqlcode != ANSI_SQL_OK) && (sqlca.sqlcode != ANSI_SQL_NOTFOUND))
    {
        fprintf (stderr, "%s - ERROR: updating t_mc_pmp_morbidity "
                "for sak_pmp_ser_loc [%d], cde_morbidity [%s] on morbidity date [%d]\n", FNC,
                sql_parm_sak_pmp_ser_loc, sql_pntl_rcd.cde_morbidity, sql_max_dte_morbidity);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }
    else
    {
        if (sqlca.sqlcode == ANSI_SQL_NOTFOUND)
        {
            EXEC SQL
              INSERT INTO t_mc_pmp_morbidity
              VALUES (:sql_parm_sak_pmp_ser_loc, :sql_pntl_rcd.cde_morbidity, :sql_max_dte_morbidity, 1);

            if (sqlca.sqlcode != ANSI_SQL_OK)
            {
                fprintf (stderr, "%s - ERROR: inserting into t_mc_pmp_morbidity "
                        "for sak_pmp_ser_loc [%d], cde_morbidity [%s], morbidity date [%d], count = 1\n", FNC,
                        sql_parm_sak_pmp_ser_loc, sql_pntl_rcd.cde_morbidity, sql_max_dte_morbidity);
                sqlerror();
                ind_abend = mgd_TRUE;
                finalize();
                exit(FAILURE);
            }
        }
    }

// RDL - BEGIN Commenting out letter generation call on 5/23/13...
//    if ( strcmp(parm_assgn_rsn, START_RSN_96_CONTINUITY_OF_PLAN) == 0 )
//    {
//        fprintf(err_rpt_file_ptr, "\t\tAssignment start reason is 96 (Default - Continuity of Plan).  Letter should not be generated.\n");
//    }
//    else if ( strcmp(parm_assgn_rsn, START_RSN_93_90_DAY_RETRO_REATTACH) == 0 )
//    {
//        fprintf(err_rpt_file_ptr, "\t\tAssignment start reason is 93 (90 Day Retro-reattach).  Letter should not be generated.\n");
//    }
//    else
//    {
//        // Re-query the potential table for this beneficiary to see if the letter function had previously
//        // set their letter indicator to 'N' because they had already been included in a letter to the entire case...
//        EXEC SQL
//         SELECT COUNT(*)
//           INTO :sql_count
//           FROM t_re_mc_recip
//          WHERE sak_recip = :sql_pntl_rcd.sak_recip
//            AND sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
//            AND ind_letter = 'Y';
//
//        if (sqlca.sqlcode != ANSI_SQL_OK)
//        {
//            fprintf (stderr, "%s - ERROR: counting record from t_re_mc_recip "
//                    "for sak_recip [%d], sak_pub_hlth [%d], ind_letter = 'Y'\n", FNC,
//                    sql_pntl_rcd.sak_recip, sql_pntl_rcd.sak_pub_hlth);
//            sqlerror();
//            ind_abend = mgd_TRUE;
//            finalize();
//            exit(FAILURE);
//        }
//
//        if (sql_count > 0)
//        {
//            fprintf(err_rpt_file_ptr, "\t\tLetter has not yet been requested for this case - requesting now.\n");
//            // Send out the initial KanCare enrollment letter...
//            rc_cfunc = c_ltr_mgdKanCareOngoing(&rtn_msg_str, sql_pntl_rcd.sak_recip, dte_current, parm_assgn_dte);
//            check_rc(&rtn_msg_str, rc_cfunc, FNC);
//        }
//        else
//        {
//            fprintf(err_rpt_file_ptr, "\t\tLetter has already been requested for this case - skipping request.\n");
//        }
//    }
// RDL - END Commenting out letter generation call on 5/23/13...

// OK, I'm trying to populate the underlying table (t_mc_mco_choice) and apparently the report is supposed to state what action was taken.
// The cde_assignment_action has the following description:
//     Result of the action. A - Accepted, N - Newborn, S - Already assigned, 9 - 90 day retro reattach.
// I think that Accepted is self-explanatory.  I'm not sure what to do with the other codes.  Any ideas?



// The State wants to know why a Choice was not used when received on the file.  Example, we received a choice on the KAECSES file,
// but it was not used because the beneficiary was assigned through the Newborn logic.  Example, a choice is received with March
// eligibility but beneficiary is not assigned to the choice because the beneficiary already has a KanCare assignment that is continuing.

//

    if (recip_mco_choice_count > 0)
    {
        // Identify the MCO Choice record that I need to update...
        EXEC SQL
         SELECT chc_sak_short
           INTO :sql_chc_sak_short
           FROM (
             SELECT chc.sak_short AS chc_sak_short
               FROM t_mc_mco_choice chc, t_pmp_svc_loc pmp
              WHERE chc.sak_recip = :sql_pntl_rcd.sak_recip
                AND pmp.sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
                AND DECODE(chc.cde_mco_choice, :UNITED_PRAP_SQL, :UNITED_SAK_PROV_SQL, :SUNFLOWER_PRAP_SQL, :SUNFLOWER_SAK_PROV_SQL, :AMERIGROUP_PRAP_SQL, :AMERIGROUP_SAK_PROV_SQL) = pmp.sak_prov
                AND TO_CHAR(chc.dte_added, 'YYYYMMDDHH24MISS') > :sql_mco_choice_last_run_datetime
                ORDER BY chc.dte_benefit_month DESC, chc.sak_short)
           WHERE ROWNUM < 2;

        if ( (sqlca.sqlcode != ANSI_SQL_OK) )
        {
            fprintf (stderr, "   ERROR: could not run the MCO Choice (sak_short) query for sak_recip [%d], sak_pub_hlth [%d], dte_added > sql_mco_choice_last_run_datetime [%ld]!\n",
                sql_pntl_rcd.sak_recip, sql_pntl_rcd.sak_pub_hlth, sql_mco_choice_last_run_datetime);
            fflush(stderr);
            sqlerror();
            ind_abend = mgd_TRUE;
            finalize();
            exit(FAILURE);
        }

        if ( strcmp(parm_assgn_rsn, START_RSN_A1_CHOICE_VIA_MEDICAID_APP) == 0 )
            // A = START_RSN_A1_CHOICE_VIA_MEDICAID_APP (Accepted)
            strcpy(sql_cde_assignment_action, "A");
        else if ( strcmp(parm_assgn_rsn, START_RSN_97_RETRO_ASSIGNMENT) == 0 )
            // S = START_RSN_97_RETRO_ASSIGNMENT (Already assigned)
            strcpy(sql_cde_assignment_action, "S");
        else if ( strcmp(parm_assgn_rsn, START_RSN_08_NEWBORN) == 0 )
            // N = START_RSN_08_NEWBORN (Newborn)
            strcpy(sql_cde_assignment_action, "N");
        else
            // 9 = START_RSN_93_90_DAY_RETRO_REATTACH (90 day retro reattach)
            strcpy(sql_cde_assignment_action, "9");

        if (arg_run_level == DAILY)
            fprintf(err_rpt_file_ptr, "\t\t\t\t\t\tUpdating MCO Choice Report Information (sak_short [%d]) with a cde_action of [%s]...\n",
                sql_chc_sak_short, sql_cde_assignment_action);
        else
            fprintf(err_rpt_file_ptr, "\t\t\tUpdating MCO Choice Report Information (sak_short [%d]) with a cde_action of [%s]...\n",
                sql_chc_sak_short, sql_cde_assignment_action);

        EXEC SQL
          UPDATE t_mc_mco_choice
             SET cde_assignment_action = :sql_cde_assignment_action
           WHERE sak_recip = :sql_pntl_rcd.sak_recip
             AND sak_short = :sql_chc_sak_short;

        if (sqlca.sqlcode != ANSI_SQL_OK)
        {
            fprintf (stderr, "%s - ERROR: Could not update T_MC_MCO_CHOICE record for sak_recip [%d], sak_short [%d]!\n",
                FNC, sql_pntl_rcd.sak_recip, sql_chc_sak_short);
            sqlerror();
            exit(FAILURE);
        }
    }

    /* Increment PMP's actual panel size... */
    //update_pmp_panel_size(parm_pmp_svc_loc);

    recip_assigned = mgd_TRUE;

    if ( parm_sak_pub_hlth == sak_pub_hlth_kc19 )
       cnt_kc19_assign++;
    else if ( parm_sak_pub_hlth ==  sak_pub_hlth_kc21 )
       cnt_kc21_assign++;


   return (0);
}

/****************************************************************************/
/*                                                                          */
/* Function:     check_pgm_elig()                                           */
/*                                                                          */
/* Description:  This function will determine if the beneficiary is         */
/*               eligible for the manage care health program.               */
/*               It will set recip_sak_cde_aid if eligible.                 */
/*                                                                          */
/* CO     Date       SE             Change                                  */
/* ------ ---------- -------------- -----------------------------           */
/*                                                                          */
/****************************************************************************/
static int check_pgm_elig(int parm_sak_pub_hlth, int parm_date)
{

    char FNC[] = "check_pgm_elig";
    int rc = mgd_FALSE;
    int incl_specified_pgm = mgd_TRUE;
    int  messaging_on = mgd_TRUE;
    char daily_monthly_run = 'D';  /* daily_monthly_run is always set to 'D' for Autoassignment */
    int dte_effective = -1;
    struct mc_eligible     mc_elig_info;
    struct mc_rtn_message  mc_msg;
    struct mc_aid_elig     mc_aid_info;


    /*recip_cde_pgm_health is set by det_pgm_assign_dte(), which needs to be called */
    /* prior to calling this function, for the current plan*/

    rc = c_is_mc_eligible_2(&mc_elig_info, &mc_msg, &mc_aid_info,
                          sql_pntl_rcd.sak_recip, parm_sak_pub_hlth,
                          parm_date, messaging_on, incl_specified_pgm, daily_monthly_run);

    if (rc == mgd_TRUE)
    {
        recip_sak_cde_aid = mc_aid_info.sak_cde_aid;
        if (arg_rsn_rpting == mgd_TRUE)
        {
           fprintf(err_rpt_file_ptr, "\t\tBene is eligible for sak_pub_hlth [%d->%s] on [%d]\n", parm_sak_pub_hlth,
                 recip_cde_pgm_health, parm_date);
        }
    }
    else
    {
        if (rc == mgd_FALSE)
        {
            if (arg_rsn_rpting == mgd_TRUE)
            {
                fprintf(err_rpt_file_ptr, "\t\tBene is NOT eligible for sak_pub_hlth [%d->%s] due to [%s] on [%d]\n", parm_sak_pub_hlth,
                      recip_cde_pgm_health,mc_elig_info.elig_msg_short, parm_date);
            }

        }
        else
        {
            c_do_display_rtn_msg(&mc_msg, FNC);
            ROLLBACK();
            ind_abend = mgd_TRUE;
            finalize();
            exit(FAILURE);
        }
    }

    return(rc);
}

/****************************************************************************/
/*                                                                          */
/* Function:     display_pmp_info()                                         */
/*                                                                          */
/* Description:  This function displays additional information for          */
/*               the specified PMP service location.                        */
/*                                                                          */
/*                                                                          */
/* Date       CO    SE                  Description                         */
/* ---------- ----- ------------------- ----------------------------------  */
/*                                                                          */
/****************************************************************************/
static void display_pmp_info(int parm_sak_pmp_ser_loc, int parm_date)
{
    char FNC[] = "display_pmp_info()";
    int rc = 0;
    struct mc_pmp_svc_loc pmp_str;
    struct mc_rtn_message  mc_rtn_msg;

    rc = c_get_pmp_svc_loc(&pmp_str, &mc_rtn_msg, parm_sak_pmp_ser_loc);

    if (rc != FUNCTION_SUCCESSFUL)
    {
        c_do_display_rtn_msg(&mc_rtn_msg, FNC);
        ROLLBACK();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    if (arg_run_level == DAILY)
        fprintf(err_rpt_file_ptr, "\t\t\t\t\tBene assigned to sak_prov [%d], location [%s], effective [%d]\n",
                 pmp_str.sak_prov, pmp_str.cde_service_loc, parm_date);
    else
        fprintf(err_rpt_file_ptr, "\t\t\tBene assigned to sak_prov [%d], location [%s], effective [%d]\n",
                 pmp_str.sak_prov, pmp_str.cde_service_loc, parm_date);

    return;
}

/****************************************************************************/
/*                                                                          */
/* Function:     display_final_counts()                                     */
/*                                                                          */
/* Description:  This function displays additional information for          */
/*               the specified PMP service location.                        */
/*                                                                          */
/* Date       CO    SE                  Description                         */
/* ---------- ----- ------------------- ----------------------------------  */
/*                                                                          */
/****************************************************************************/
static void display_final_counts(void)
{
    char FNC[] = "display_final_counts()";
    int rc = 0;
    int cursor_rc = 0;

    fprintf(err_rpt_file_ptr, "\n\nFinal Assignment Counts:\n\n");
    fprintf(err_rpt_file_ptr, "\tMorbidity:                [%d]\n", morbidity_cnt);
    fprintf(err_rpt_file_ptr, "\tMCO Continuity:           [%d]\n", pmp_chg_cnt);
    fprintf(err_rpt_file_ptr, "\tCase Logic:               [%d]\n\n", case_logic_cnt);

    fprintf(stderr, "\n\nFinal Assignment Counts:\n\n");
    fprintf(stderr, "\tMorbidity:                [%d]\n", morbidity_cnt);
    fprintf(stderr, "\tMCO Continuity:           [%d]\n", pmp_chg_cnt);
    fprintf(stderr, "\tCase Logic:               [%d]\n\n", case_logic_cnt);

    fprintf(stderr, "\nOpening morbidity_rpt cursor...  ");
    fflush(stderr);
    EXEC SQL open morbidity_rpt;

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "   ERROR: could not open the morbidity_rpt Cursor\n");
        sqlerror();
        exit(FAILURE);
    }
    else
        fprintf(stderr, "Opened.\n\n");

    fflush(stderr);

    cursor_rc = fetch_rpt_info();

    fprintf(err_rpt_file_ptr, "\n\nMCO ID\tMorbidity Code\tTotal Beneficiaries\n");
    fprintf(stderr, "\n\nMCO ID\tMorbidity Code\tTotal Beneficiaries\n");

    while (cursor_rc == ANSI_SQL_OK)
    {
        fprintf(err_rpt_file_ptr, "%s%s\t%s\t%d\n", sql_rpt_id_provider, sql_rpt_cde_service_loc, sql_rpt_cde_morbidity, sql_rpt_num_count);
        fprintf(stderr, "%s%s\t%s\t%d\n", sql_rpt_id_provider, sql_rpt_cde_service_loc, sql_rpt_cde_morbidity, sql_rpt_num_count);
        fflush(stderr);

        cursor_rc = fetch_rpt_info();
    }

    fprintf(err_rpt_file_ptr, "\n\n");

    EXEC SQL close morbidity_rpt ;
    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "  ERROR: could not close the morbidity_rpt Cursor\n");
        sqlerror();
        exit(FAILURE);
    }

    return;
}

/****************************************************************************/
/*                                                                          */
/* Function:  display_rcd_cnt()                                             */
/*                                                                          */
/* Description:  Display the number of records processed.                   */
/*                                                                          */
/*                                                                          */
/****************************************************************************/
/*                                                                          */
/*  Date        CO      Author          Description                         */
/*  ----------  ------  ----------      ---------------------------------   */
/*                                                                          */
/****************************************************************************/
static void display_rcd_cnt(void)
{
    char FNC[] = "display_rcd_cnt()";
    time_t  process_time_curr;
    static time_t  process_time_prev;
    double process_seconds_overall = 0.0;
    double process_seconds_curr = 0.0;
    double avg_seconds_per_rcd_overall = 0;
    double avg_seconds_per_rcd_curr = 0;
    double seconds_remaining_overall = 0;
    double seconds_remaining_curr = 0;
    int   rcd_cnt_prev = 0;

    /* retrieve the time stamp from the operating system... */
    mark_date(date_stamp);
    mark_time(time_stamp);

    /* Display some counts so we can tell how far along we are... */
    if ((rcd_cnt == 1) ||
        (rcd_cnt == 5) ||
        (rcd_cnt == 10) ||
        (rcd_cnt == 50) ||
        (rcd_cnt == 100) ||
        ((rcd_cnt > 0) && (rcd_cnt % 1000) == 0))
    {
        if (rcd_cnt == 1)
            fprintf(stderr, "\n\tProcessing began on [%s  %s] with a total of [%d] records to read...\n",
                    date_stamp_initial, time_stamp_initial, sql_num_total_rcds);

        process_time_curr = time(NULL);
        process_seconds_overall = difftime(process_time_curr, process_start_time);
        process_seconds_curr = difftime(process_time_curr, process_time_prev);
        avg_seconds_per_rcd_overall = process_seconds_overall/rcd_cnt;
        avg_seconds_per_rcd_curr = process_seconds_curr/rcd_cnt_prev;
        seconds_remaining_overall = avg_seconds_per_rcd_overall * (sql_num_total_rcds - rcd_cnt);
        seconds_remaining_curr = avg_seconds_per_rcd_curr * (sql_num_total_rcds - rcd_cnt);

        /* Store the current time for the next display calculations... */
        process_time_prev = time(NULL);
        rcd_cnt_prev = rcd_cnt;

        if (rcd_cnt > 10)
        {
            fprintf(stderr,   "\tAveraging [%.2f] seconds per record overall, with a current average of [%.2f].\n",
                avg_seconds_per_rcd_overall, avg_seconds_per_rcd_curr);
            fprintf(stderr,   "\tEst overall process hours remaining is [%.2f], with a current rate at [%.2f] hours\n",
                ((seconds_remaining_overall/60)/60), ((seconds_remaining_curr/60)/60));

            fprintf(stderr, "\n\tCount of Potential records read ....................... [%9d]\n", cnt_potential_read);
            fprintf(stderr, "\t\t# of Potential records eligible ................. [%9d]\n", cnt_elig);
            fprintf(stderr, "\t\t# of Potential records not eligible.............. [%9d]\n", cnt_not_elig);
            fprintf(stderr, "\t\t# of KC19 Assignments .......................... [%9d]\n", cnt_kc19_assign);
            fprintf(stderr, "\t\t# of KC21 Assignments .......................... [%9d]\n", cnt_kc21_assign);
            fprintf(stderr, "\t\t# of Potential records deleted for ineligibility. [%9d]\n", cnt_pot_delete);
            fflush(stderr);
        }

        fprintf(stderr, "\n\tCount of records read as of [%s  %s]....... [%9d]\n", date_stamp, time_stamp, rcd_cnt);

    }
}

/****************************************************************************/
/*                                                                          */
/*  Function Name: count_total_rcds()                                       */
/*                                                                          */
/*  Description:                                                            */
/*                                                                          */
/****************************************************************************/
static void count_total_rcds(void)
{
    char * FNC = "count_total_rcds()";

    fprintf(stderr, "Counting total records that will be processed...  ");
    EXEC SQL
     SELECT count(*)
       INTO :sql_num_total_rcds
       FROM t_re_mc_recip
      WHERE sak_pub_hlth in (80, 81);

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf(stderr, "%s - ERROR counting total records! \n", FNC);
        sqlerror();
        exit(FAILURE);
    }


    fprintf(stderr, "Total record count is [%d].\n\n", sql_num_total_rcds);

    return;
}

/****************************************************************************/
/*                                                                          */
/* Function:    mark_date()                                                 */
/*                                                                          */
/* Description: Convert current date to the MM/DD/CCYY format               */
/*                                                                          */
/****************************************************************************/
static void mark_date(char* parm_date_stamp)
{
   time_t curr_time_tmp;
   int len = sizeof("MM/DD/CCYY") + 1;

   curr_time_tmp = time(NULL);

   strftime( parm_date_stamp, len, "%m/%d/%Y", localtime(&curr_time_tmp) );
}

/****************************************************************************/
/*                                                                          */
/* Function:    mark_time()                                                 */
/*                                                                          */
/* Description: Convert current time to the HH:MM:SS format                 */
/*                                                                          */
/****************************************************************************/
static void mark_time(char* parm_time_stamp)
{
    time_t curr_time_tmp;
    int len = sizeof("HH:MM:SS") + 1;

    curr_time_tmp = time(NULL);
    strftime( parm_time_stamp, len, "%H:%M:%S", localtime(&curr_time_tmp) );
}

/****************************************************************************/
/*                                                                          */
/* Function:    is_newborn()                                                */
/*                                                                          */
/* Description: This function determines if the bene is a newborn by        */
/*              looking at the bene's age and whether they have had a       */
/*              previous PMP assignment.                                    */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static int is_newborn(void)
{

    int rc = mgd_FALSE;
    char FNC[] = "is_newborn()";

    EXEC SQL BEGIN DECLARE SECTION;
        int sql_count = 0;
    EXEC SQL END DECLARE SECTION;

    if ( c_do_calc_recip_age(recip_dte_birth, dte_current) == 0)
    {
        if (arg_rsn_rpting == mgd_TRUE)
        {
            fprintf(err_rpt_file_ptr, "\tBene is less than 1 yr old, checking for previous PMP assignment...  ");
        }

        /* Count the total number of assignments the bene has ever had... */
        EXEC SQL
        SELECT count(*)
          INTO :sql_count
          FROM t_re_pmp_assign
         WHERE sak_recip = :sql_pntl_rcd.sak_recip;

        if (sqlca.sqlcode != ANSI_SQL_OK)
        {
            fprintf (stderr, "%s - ERROR counting the number of newborn assignments!\n",FNC);
            sqlerror();
            ind_abend = mgd_TRUE;
            finalize();
            exit(FAILURE);
        }

        /* if the bene has NEVER had an assignment, then they ARE considered a newborn... */
        if (sql_count == 0)
        {
            rc = mgd_TRUE;
            if (arg_rsn_rpting == mgd_TRUE)
            {
                fprintf(err_rpt_file_ptr, "None found - bene IS newborn.\n");
            }
        }
        else
        {
            rc = mgd_FALSE;
            if (arg_rsn_rpting == mgd_TRUE)
            {
                fprintf(err_rpt_file_ptr, "Found - bene is NOT newborn.\n");
            }
        }

    }

    return(rc);

}

/****************************************************************************/
/*                                                                          */
/* Function:    is_female_pw()                                              */
/*                                                                          */
/* Description: This function determines if the bene is a female pregnant   */
/*              woman.                                                      */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static int is_female_pw(int parm_dte)
{

    int rc = mgd_FALSE;
    char FNC[] = "is_female_pw()";

    EXEC SQL BEGIN DECLARE SECTION;
        int sql_count = 0;
        int sql_parm_dte = parm_dte;
    EXEC SQL END DECLARE SECTION;

    EXEC SQL
     SELECT count(*)
       INTO :sql_count
       FROM t_re_aid_elig aelig, t_re_elig elig, t_cde_aid aid, t_re_base base
      WHERE elig.sak_recip = :sql_pntl_rcd.sak_recip
        AND elig.sak_recip = base.sak_recip
        AND elig.sak_pub_hlth IN (SELECT sak_pub_hlth
                                    FROM t_pub_hlth_pgm
                                   WHERE cde_pgm_health IN ('TXIX', 'TXXI', 'MN', 'P19', 'P21'))
        AND elig.dte_effective <= TO_CHAR(LAST_DAY(TO_DATE(:sql_parm_dte, 'YYYYMMDD')), 'YYYYMMDD')
        AND elig.dte_end >= :sql_parm_dte
        AND elig.cde_status1 <> 'H'
        AND aelig.sak_recip = elig.sak_recip
        AND aelig.sak_pgm_elig = elig.sak_pgm_elig
        AND aelig.dte_effective <= TO_CHAR(LAST_DAY(TO_DATE(:sql_parm_dte, 'YYYYMMDD')), 'YYYYMMDD')
        AND aelig.dte_end >= :sql_parm_dte
        AND aelig.cde_status1 <> 'H'
        AND aelig.sak_cde_aid = aid.sak_cde_aid
        AND aid.cde_aid_category = '44'
        AND base.cde_sex = 'F';

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: Could not determine if sak_recip [%d] is a female with pop code 44!\n", FNC, sql_pntl_rcd.sak_recip);
        sqlerror();
        exit(FAILURE);
    }

    if (sql_count > 0)
    {
        fprintf(err_rpt_file_ptr, "\t\tBeneficiary is female with PW aid category for benefit month [%d].\n", dte_kc_elig_verification);
        rc = mgd_TRUE;
    }
    else
    {
        fprintf(err_rpt_file_ptr, "\t\tBeneficiary is NOT female with PW aid category for benefit month [%d].\n", dte_kc_elig_verification);
        rc = mgd_FALSE;
    }


    return(rc);

}

/****************************************************************************/
/*                                                                          */
/* Function:    is_presumptive()                                            */
/*                                                                          */
/* Description: This function determines if the bene is a newborn by        */
/*              looking at the bene's age and whether they have had a       */
/*              previous PMP assignment.                                    */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static int is_presumptive(void)
{
    int rc = mgd_FALSE;
    char FNC[] = "is_presumptive()";

    EXEC SQL BEGIN DECLARE SECTION;
        int sql_elig_sak_pub_hlth = 0;
    EXEC SQL END DECLARE SECTION;

    /* Count the total number of assignments the bene has ever had... */
    EXEC SQL
     SELECT elig_sak_pub_hlth
       INTO :sql_elig_sak_pub_hlth
       FROM (
            SELECT elig.sak_pub_hlth AS elig_sak_pub_hlth
              FROM t_re_elig elig, t_pub_hlth_pgm pgm
             WHERE elig.sak_recip = :sql_pntl_rcd.sak_recip
               AND elig.sak_pub_hlth = pgm.sak_pub_hlth
               AND pgm.cde_pgm_health in ('TXIX', 'TXXI', 'MN', 'P19', 'P21')
               AND elig.cde_status1 = ' '
           ORDER BY elig.dte_end DESC)
        WHERE rownum < 2;

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR retrieving possible presumptive eligibility!\n",FNC);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    /* if the bene has NEVER had an assignment, then they ARE considered a newborn... */
    if ((sql_elig_sak_pub_hlth == P19_SAK_PUB_HLTH) || (sql_elig_sak_pub_hlth == P21_SAK_PUB_HLTH))
    {
        rc = mgd_TRUE;
        fprintf(err_rpt_file_ptr, "\tMost recent eligibility on file is presumptive.\n");
    }
    else
    {
        rc = mgd_FALSE;
        fprintf(err_rpt_file_ptr, "\tMost recent eligibility on file is NOT presumptive.\n");
    }

    return(rc);
}

/****************************************************************************/
/*                                                                          */
/* Function:     assign_newborn()                                           */
/*                                                                          */
/* Description:  Try to assign newborn by finding mother's PMP              */
/*                                                                          */
/* Date       CO     SE             Description                             */
/* ---------- ------ -------------- --------------------------------------  */
/*                                                                          */
/****************************************************************************/
static int assign_newborn(void)
{
    char FNC[] = "assign_newborn()";

    int rc = mgd_FALSE;
    int rc_cfunc = mgd_FALSE;


    struct mc_pmp_assign_info mom_pmp_data_str;
    struct mc_is_reason pmp_rsn_str;
    struct mc_rtn_message rtn_msg_str;
    struct valid_pmp_info_type valid_pmp_str;
    int messaging_on = mgd_TRUE;
    int override_panel_sz = mgd_TRUE;
    int rc_elig = mgd_FALSE;
    int unique_pmp = mgd_FALSE;

    EXEC SQL BEGIN DECLARE SECTION;
        int    sql_sak_recip_mom;
        int    sql_sak_dte_birth_mom;
        int    sql_case_mom;
        int    sql_ptl_mom_cnt = 0;
        int    sql_ptl_mom_distinct_mco_cnt = 0;
        int    sql_recip_dte_birth;
        int    sql_recip_dte_tmp;
        int    sql_ptl_mom_sak_pmp_ser_loc = 0;
        int    sql_ptl_mom_sak_prov = 0;
    EXEC SQL END DECLARE SECTION;

    if (recip_dte_birth < sql_dte_kc_golive)
    {
        sql_recip_dte_birth = sql_dte_kc_golive;
        fprintf(err_rpt_file_ptr, "\t\tDate of birth [%d] is prior to KanCare go-live.  Utilizing KanCare go-live date [%d] instead of actual DOB....\n",
              recip_dte_birth, sql_dte_kc_golive );
        fflush(err_rpt_file_ptr);
    }
    else
    {
        sql_recip_dte_birth = recip_dte_birth;
        fprintf(err_rpt_file_ptr, "\t\tDate of birth [%d] is NOT prior to KanCare go-live [%d].  Utilizing actual DOB....\n",
              recip_dte_birth, sql_dte_kc_golive );
        fflush(err_rpt_file_ptr);
    }

    // Verify that the newborn is eligible as of their DOB, if not, then there's no reason to continue...
    rc_elig = check_pgm_elig(sql_pntl_rcd.sak_pub_hlth, sql_recip_dte_birth);

    if (rc_elig == mgd_FALSE)
    {
        fprintf(err_rpt_file_ptr, "\t\tNewborn not eligible for KanCare on date of birth [%d].  Proceeding with next branches of logic.\n",
                sql_recip_dte_birth );
        fflush(err_rpt_file_ptr);
        return(mgd_FALSE);
    }

    // The newborn was eligible on their DOB, so try to find a mom who was assigned at that point in time...
    EXEC SQL
     SELECT  COUNT(*)
       INTO :sql_ptl_mom_cnt
       FROM t_re_base base, t_re_elig elig
      WHERE base.sak_case = :recip_sak_case
        AND base.cde_sex = 'F'
        AND MONTHS_BETWEEN(TO_DATE(:dte_current,'YYYYMMDD'),TO_DATE(base.dte_birth,'YYYYMMDD'))/12 >= :pot_mother_age
        AND MONTHS_BETWEEN(TO_DATE(:dte_current,'YYYYMMDD'),TO_DATE(base.dte_birth,'YYYYMMDD'))/12 < 56
        AND base.sak_Recip = elig.sak_recip
        AND elig.sak_pub_hlth IN (80,81)
        AND elig.dte_effective <= :sql_recip_dte_birth
        AND elig.dte_end >= :sql_recip_dte_birth
        AND elig.cde_status1 = ' ';

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "   ERROR: could not count number of potential mother assignments!\n");
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    if (sql_ptl_mom_cnt > 0)
    {
        if (arg_rsn_rpting == mgd_TRUE)
        {
            fprintf(err_rpt_file_ptr, "\t\tA total of [%d] potential mothers are enrolled in KanCare on date of birth [%d]....\n",
                    sql_ptl_mom_cnt, sql_recip_dte_birth );
            fflush(err_rpt_file_ptr);
        }

        EXEC SQL
         SELECT  COUNT(DISTINCT(pmp.sak_prov))
           INTO :sql_ptl_mom_distinct_mco_cnt
           FROM t_re_base base, t_re_elig elig, t_re_pmp_assign asgn, t_pmp_svc_loc pmp
          WHERE base.sak_case = :recip_sak_case
            AND base.cde_sex = 'F'
            AND MONTHS_BETWEEN(TO_DATE(:dte_current,'YYYYMMDD'),TO_DATE(base.dte_birth,'YYYYMMDD'))/12 >= :pot_mother_age
            AND MONTHS_BETWEEN(TO_DATE(:dte_current,'YYYYMMDD'),TO_DATE(base.dte_birth,'YYYYMMDD'))/12 < 56
            AND base.sak_Recip = elig.sak_recip
            AND elig.sak_pub_hlth IN (80,81)
            AND elig.dte_effective <= :sql_recip_dte_birth
            AND elig.dte_end >= :sql_recip_dte_birth
            AND elig.cde_status1 = ' '
            AND elig.sak_recip = asgn.sak_Recip
            AND elig.sak_pgm_elig = asgn.sak_pgm_elig
            AND asgn.sak_pmp_ser_loc = pmp.sak_pmp_ser_loc;

        if (sqlca.sqlcode != ANSI_SQL_OK)
        {
            fprintf (stderr, "   ERROR: could not count number of potential mother distinct MCOs!\n");
            sqlerror();
            ind_abend = mgd_TRUE;
            finalize();
            exit(FAILURE);
        }

        if (sql_ptl_mom_distinct_mco_cnt == 1)
        {
            EXEC SQL
             SELECT  pmp.sak_prov
               INTO :sql_ptl_mom_sak_prov
               FROM t_re_base base, t_re_elig elig, t_re_pmp_assign asgn, t_pmp_svc_loc pmp
              WHERE base.sak_case = :recip_sak_case
                AND base.cde_sex = 'F'
                AND MONTHS_BETWEEN(TO_DATE(:dte_current,'YYYYMMDD'),TO_DATE(base.dte_birth,'YYYYMMDD'))/12 >= :pot_mother_age
                AND MONTHS_BETWEEN(TO_DATE(:dte_current,'YYYYMMDD'),TO_DATE(base.dte_birth,'YYYYMMDD'))/12 < 56
                AND base.sak_Recip = elig.sak_recip
                AND elig.sak_pub_hlth IN (80,81)
                AND elig.dte_effective <= :sql_recip_dte_birth
                AND elig.dte_end >= :sql_recip_dte_birth
                AND elig.cde_status1 = ' '
                AND elig.sak_recip = asgn.sak_Recip
                AND elig.sak_pgm_elig = asgn.sak_pgm_elig
                AND asgn.sak_pmp_ser_loc = pmp.sak_pmp_ser_loc
                AND rownum < 2;

            if (sqlca.sqlcode != ANSI_SQL_OK)
            {
                fprintf (stderr, "   ERROR: could not retrieve potential mother distinct MCOs!\n");
                sqlerror();
                ind_abend = mgd_TRUE;
                finalize();
                exit(FAILURE);
            }

            EXEC SQL
             SELECT sak_pmp_ser_loc
               INTO :sql_ptl_mom_sak_pmp_ser_loc
               FROM t_pmp_svc_loc
              WHERE sak_prov = :sql_ptl_mom_sak_prov
                AND sak_pub_hlth = :sql_pntl_rcd.sak_pub_hlth
                AND dte_effective <= :sql_dte_enroll_pgm
                AND dte_end > :sql_dte_enroll_pgm ;

            if (sqlca.sqlcode != ANSI_SQL_OK)
            {
                fprintf (stderr, "   ERROR: could not run the newborn query for sak_prov [%d], sak_pub_hlth [%d] that's "
                        "attempting to find the other service location.\n",
                        sql_ptl_mom_sak_prov, sql_pntl_rcd.sak_pub_hlth);
                sqlerror();
                ind_abend = mgd_TRUE;
                finalize();
                exit(FAILURE);
            }


            /* T21, P19 & P21 need to use program eligibility date instead of DOB */  
            if (sql_pntl_rcd.sak_pub_hlth == sak_pub_hlth_kc21 || recip_is_presumptive == mgd_TRUE)
            {
                EXEC SQL
                    SELECT dte_effective
                      INTO :sql_recip_dte_tmp
                      FROM t_re_elig
                     WHERE sak_recip = :sql_pntl_rcd.sak_recip
                       AND sak_pub_hlth IN (:TXIX_SAK_PUB_HLTH,
                                            :TXXI_SAK_PUB_HLTH,
                                            :MN_SAK_PUB_HLTH,
                                            :P19_SAK_PUB_HLTH,
                                            :P21_SAK_PUB_HLTH)
                       AND dte_effective <= TO_CHAR(LAST_DAY(TO_DATE(:sql_recip_dte_birth, 'YYYYMMDD')), 'YYYYMMDD') //last day of birth month
                       AND dte_end       >= TO_CHAR(ADD_MONTHS(LAST_DAY(TO_DATE( :sql_recip_dte_birth, 'YYYYMMDD')) + 1, -1), 'YYYYMMDD')  //first day of birth month
                       AND cde_status1   <> 'H' ;

                if (sqlca.sqlcode != ANSI_SQL_OK)
                {
                    fprintf (stderr, "   ERROR: could not get newborn's [sak_recip %d] program eligibility date using DOB [%d].\n",
                            sql_pntl_rcd.sak_recip, sql_recip_dte_birth);
                    sqlerror();
                    ind_abend = mgd_TRUE;
                    finalize();
                    exit(FAILURE);
                }

                /*Assign newborn to potential mother's KanCare PMP */
                if (arg_rsn_rpting == mgd_TRUE)
                {
                  fprintf(err_rpt_file_ptr, "\t\tAttempting retro-assign to KanCare MCO [%d] on newborn eligibility date [%d]....\n",
                          sql_ptl_mom_sak_pmp_ser_loc, sql_recip_dte_tmp );
                  fflush(err_rpt_file_ptr);
                }
            }
            else
            {
                /*Assign newborn to potential mother's KanCare PMP */
                if (arg_rsn_rpting == mgd_TRUE)
                {
                  fprintf(err_rpt_file_ptr, "\t\tAttempting retro-assign to KanCare MCO [%d] on date of birth's [%d] FOM....\n",
                          sql_ptl_mom_sak_pmp_ser_loc, sql_recip_dte_birth );
                  fflush(err_rpt_file_ptr);
                }
                sql_recip_dte_tmp = c_get_month_begin(sql_recip_dte_birth);
            }
                                              
            make_pmp_assgn(sql_ptl_mom_sak_pmp_ser_loc, START_RSN_08_NEWBORN,
                          (sql_pntl_rcd.sak_pub_hlth == sak_pub_hlth_kc21) ? SAK_ENT_AUTOASSIGN_NEWBORN_KC21 : SAK_ENT_AUTOASSIGN_NEWBORN_KC19,
                           sql_recip_dte_tmp, 
                           sql_pntl_rcd.sak_pub_hlth, 0, " ");

            rc = mgd_TRUE;

        }
        else
        {

            if (arg_rsn_rpting == mgd_TRUE)
            {
              fprintf(err_rpt_file_ptr, "\t\tPotential mothers found, but not all are enrolled with the same MCO.  Sending bene to \"Newborns Not Retro-Assigned To An MCO\" Report\n");
              fflush(err_rpt_file_ptr);
            }
            memset(&newborn_rpt_rec,'\0',sizeof(newborn_rpt_rec));
            newborn_rpt_rec.sak_recip = sql_pntl_rcd.sak_recip;
            newborn_rpt_rec.sak_case = recip_sak_case;
            newborn_rpt_rec.dte_birth = recip_dte_birth;
            newborn_rpt_rec.sak_pub_hlth = -1;
            strcpy(newborn_rpt_rec.id_medicaid, recip_id_medicaid);
            strcpy(newborn_rpt_rec.reason_no_mco, NBMULTIMOMSDIFFASSIGNS);
            write_newborn_rec();

            // This newborn needs to be worked manually, so don't go any further
            recip_assigned = mgd_TRUE;

            // Remove them from the potential table so that they can't be included in a letter to another case member...
            EXEC SQL
             DELETE FROM t_re_mc_recip
              WHERE sak_recip = :sql_pntl_rcd.sak_recip
                AND sak_pub_hlth IN (80,81);

            if (sqlca.sqlcode != ANSI_SQL_OK)
            {
                fprintf (stderr, "   ERROR: could not delete the KanCare potential record for sak_recip [%d]!\n",
                        sql_pntl_rcd.sak_recip);
                sqlerror();
                ind_abend = mgd_TRUE;
                finalize();
                exit(FAILURE);
            }

            rc = mgd_TRUE;

        }
    }
    else
    {
        fprintf(err_rpt_file_ptr, "\t\tNo potential mothers found with assignments in the same program as of the DOB [%d].  Proceeding with next branches of logic.\n",
                sql_recip_dte_birth );
        fflush(err_rpt_file_ptr);
        rc = mgd_FALSE;
    }

    return(rc);

}

/****************************************************************************/
/*                                                                          */
/* Function:     write_newborn_rec()                                        */
/*                                                                          */
/* Description:  This function write the newborn data record.               */
/*               The newborn data file is used in the "Newborns             */
/*               Not assigned to an MCO" report.                            */
/*                                                                          */
/****************************************************************************/
static int write_newborn_rec()
{
    char FNC[] = "write_newborn_rec()";
    int rc = mgd_FALSE;

    if ( !fwrite(&newborn_rpt_rec, sizeof(newborn_rpt_rec), 1, nb_rpt_file_ptr ) == 1 )
    {
        fprintf (stderr, "%s ERROR: could not write to newborn data file", FNC);
        fprintf(stderr,"%d - %s\n\n",errno, strerror(errno));
        ind_abend = mgd_TRUE;
        finalize();
        exit(FUNCTION_ERROR);
    }
    return rc;

}
