/* HP Port 11517 20100115 - From pendingcheckin/temp/mgd_nm_assign.sc 1.5 */
#pragma ident "@(#)mgd_nm_assign.sc	1.8 09/25/12 EDS"
/********************************************************************************/
/*                                                                              */
/*   PROGRAM:     mgd_nm_assign                                                 */
/*                                                                              */
/*   DESCRIPTION: This program checks the NEMT eligibility and creates          */
/*                assignments for the beneficiaries in potential NEMT table     */
/*                The potential NEMT table t_mc_re_nemt is created by           */
/*                KAECSES eligibility process.                                  */
/*                                                                              */
/********************************************************************************/
/*                                                                              */
/*                     Modification Log                                         */
/*                                                                              */
/* Date     CO      Author              Description                             */
/* -------- ------  ------------------  -----------------                       */
/* 09/01/09 11976   Nirmal Thiagarajan  Initial release.                        */
/*                                                                              */
/* 10/16/09 11976   Nirmal Thiagarajan  Various fixes for errors found in MO.	*/
/*					Added all 4 benefit plan to main sql.	*/
/*					Added union with main cursor to include */
/*					spend down met beneficiaries.		*/
/*					Removed function is_recip_newly_eligible*/
/*                                                                              */
/* 11/09/09 11976   Nirmal Thiagarajan  Corrected dte_effective for spenddown   */
/*					met beneficiaries sql in main cursor.	*/
/*					Added dte_end to main sql cursor. Also  */
/*					introduced NEMT run parms MGDNMCYC to   */
/*					track previous run to pull all spenddown*/
/*					beneficiaries since last NEMT cycle.	*/
/*                                                                              */
/* 12/04/09 11976   Nirmal Thiagarajan  Modified spenddown met cursor to correct*/
/*					retro adjustment months and end date.	*/
/*										*/
/* 12/07/09 11976   Nirmal Thiagarajan  Modified spenddown met cursor's end dt.	*/
/*                                                                              */
/* 08/06/2012 14303 Dale Gallaway       KanCare - Check MGDKCINT and MGDHWEND   */
/*                                      parms to end current programs.          */
/********************************************************************************/

/*********************************************************************/
/*                         Includes                                  */
/*********************************************************************/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include <errno.h>
#include "mgd_kaecses.h"
#include "db_oci.h"
#include "hmspconn.h"
#include "mgd_common.h"

/************************************************************************/
/*                  Macro Definitions (Constants)                       */
/************************************************************************/

/* #define DBG */

#define PROGNAME         "mgd_nm_assign"
#define SPACE            ' '
#define ZERO             '0'
#define MAX_STRING       (60)

#define RETRO_ELIG_MONTH   (benefit_mth_start < last_kaecses_elig_dte)
#define NOT_PRIOR_TO_IMPL  (benefit_mth_start < POLICY_START_DATE)

/************************************************************************/
/* If you need to display the built-in debugging messages, uncomment    */
/* the following                                                        */
/************************************************************************/
/* #define DEBUG */

/************************************************************************/
/*                  Type Declarations and Internal Structures           */
/************************************************************************/
typedef int bool;

struct mc_pot_recip
{
    int sak_recip;                 /* NUMBER(9)    */
    int dte_effective;             /* NUMBER(8)    */
    int sak_pub_hlth;              /* NUMBER(9)    */
} ;

/* Define the structure used for storing variables from fetches made    */
/* in a cursor used to process though PMP Assignements for a bene.      */
struct pmp_assign_data
{
    int sak_recip;
    int sak_re_pmp_assign;
    int sak_pub_hlth;
    int dte_effective;
    int dte_end;
    int sak_pmp_ser_loc;
    int  sak_pgm_elig;
    char cde_rsn_mc_start[2+1];
};

/*********************************************************************/
/*                         DB Variables                              */
/*********************************************************************/
EXEC SQL INCLUDE SQLCA;

EXEC SQL BEGIN DECLARE SECTION;
    static struct mc_pot_recip sql_pntl_rcd;
    static struct mc_pmp_svc_loc pmp_svc_loc_str;
    static struct report_data report_rec;
    static struct pmp_assign_data pmp_assign_info;
    static struct mc_pmp_assign_info assign_info_str;
    static struct mc_rtn_message mc_rtn_msg_str;

    static const char ELIG_DEATH_REASON[2+1]    = "21";  /* Death of Beneficiary */
    static const char MC_ELIG_END_REASON[2+1]   = "59";  /* MC elig ended */
    static       char DEFAULT_NEMT_ASSIGN[2+1]  = "83";  /* Default - NEMT Assignment */
    static const char NEMT_ELIG_CHG_REASON[2+1] = "84";  /* NEMT elig criteria change */

    static int    dte_current;
    static int    dte_curr_day;
    static int    dte_enroll;
    static char    recip_id_medicaid[12+1];
    static int    prev_sak_recip = 0;
    static int    recip_sak_case = 0;
    static char    recip_cde_county[2+1];
    static char    recip_zip_code[5+1];
    static int    recip_dte_birth= -1;
    static int    recip_dte_death=0;
    static char    recip_cde_pgm_health[12+1];
    static int    recip_sak_cde_aid;

    static int    sql_date_parm_1;
    static int    sql_dte_parm_2;
    static int    sql_dte_end;

    static int    benefit_mth_end;
    static int    benefit_mth_start;
    static int    benefit_mth_prev_end_dte;
    static int    EOT = 22991231;
    static int    POLICY_START_DATE = 20091101;
    static int    mc_retro_date = 0;          /* max retro assignment date          */
    static int    aim_cycle_date = 0;         /* cycle processing date              */
    static int    last_mthly_kaecses_dte = 0; /* last run date of KAECSES monthly   */
    static int    last_kaecses_elig_dte = 0;  /* last mth KAECSES eligibility recvd */
    static int    last_kaecses_elig_end = 0;  /* last mth KAECSES eligibility recvd */
    static int    last_nemt_cyc_dte_1   = 0;  /* last NEMT cycle run date           */
    static int    last_nemt_cyc_dte_2   = 0;  /* current NEMT cycle run date        */
    static int    dte_enroll_nemt;

    static int    sak_pub_hlth_nemt;
    static int    sak_pmp_svc_nemt_cap;
    static int    sak_pmp_svc_nemt_nocap;
    static int    autoassign_nemt = mgd_FALSE;

    static int    sql_sak_pmp_svc_loc= -1;
    static int    sql_sak_pub_hlth = -1;

    static int    sql_sak_cde_aid;
    static int    sql_sak_aid_elig;
    static int    sql_aid_dte_effective;
    static int    sql_aid_dte_end;
    static int    sak_pub_hlth_mcaid;
    static int    mcaid_sak_cde_aid;
    static int    sql_sak_pgm_elig;
EXEC SQL END DECLARE SECTION;

/* Define the second connection to the database... */
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
static  char    *nb_rpt_file;
static  FILE    *err_rpt_file_ptr;
static  FILE    *nb_rpt_file_ptr;

static  int    cnt_potential_read  = 0;
static  int    cnt_nemt_assign     = 0;
static  int    cnt_nemt_extend     = 0;
static  int    cnt_nemt_report     = 0;
static  int    cnt_nemt_notelig    = 0;
static  int    cnt_nemt_continued  = 0;
static  int    date_hwend          = 0;
static  int    date_kcint          = 0;
static  int    SAK_ENT_NEMT_AUTOASSIGN = 0;
static  int    mc_sak_elig_stop = 0;
static  int    sak_re_pmp_assign = -1;

static  char    curr_time[5];
static  char    curr_date[9];
static  int    ind_abend = mgd_FALSE;

/* Global Variable Flags */
static  int     messaging_on = mgd_TRUE;
static  int     arg_rsn_rpting = mgd_FALSE;
static  int     arg_save_to_db = mgd_FALSE;
static  int     enrolled_nemt  = mgd_FALSE;
static  int     noncap_svc_loc = mgd_FALSE;
static  int     eligible_for_nemt = mgd_FALSE;

/**************************************************************************/
/*                      SQL Cursor Declarations                           */
/**************************************************************************/
/* CURSOR NAME:  all_potentials                                           */
/* DESCRIPTION:  Retrieve all records from the potential table to verify  */
/*               and try to assign.                                       */
/**************************************************************************/
EXEC SQL AT DBCONN3 DECLARE all_potentials CURSOR for
SELECT  pot.sak_recip as sak_recip,
        pot.dte_effective as dte_effective,
        pot.sak_pub_hlth_mcaid as mcaid_pgm,
        0 as dte_end
FROM    t_mc_re_nemt pot
WHERE   pot.dte_effective  >= :POLICY_START_DATE /* cannot be prior to policy impl */
  AND   pot.dte_effective  >= :mc_retro_date      /* cannot be prior to allowble retro mths */
UNION
SELECT loc.sak_recip as sak_recip,
       loc.dte_effective as dte_effective,
       elig.sak_pub_hlth as mcaid_pgm,
       0 as dte_end
FROM   t_re_elig elig,
       (SELECT distinct sak_recip,
                to_number(to_char(DTE_YYYYMM) || '01') as dte_effective,
                to_number(to_char(last_day(to_date(to_char(DTE_YYYYMM),'YYYYMM')),'YYYYMMDD')) as dte_end
           FROM t_re_loc a,
                t_pa_poc_mth b
          WHERE DTE_YYYYMM between trunc(a.DTE_EFFECTIVE/100) and trunc(a.DTE_END/100)
            AND DTE_LAST_CHANGE >= :aim_cycle_date
            and DTE_YYYYMM between trunc(:mc_retro_date/100) and trunc(:last_kaecses_elig_dte/100)
       ) loc,
       t_pub_hlth_pgm pgm
WHERE  elig.sak_recip = loc.sak_recip
AND    elig.cde_status1 <> 'H'
AND    elig.sak_pub_hlth = pgm.sak_pub_hlth
AND    pgm.cde_pgm_health in ('TXIX','MN','P19','P21')
AND    elig.dte_effective <= loc.dte_end
AND    elig.dte_end >= loc.dte_effective
AND    loc.dte_effective >= :POLICY_START_DATE
UNION
    SELECT b.sak_recip,
           (CASE WHEN a.dte_effective < :POLICY_START_DATE THEN :POLICY_START_DATE ELSE a.dte_effective END) dte_eff,
           c.sak_pub_hlth,
           (CASE WHEN a.dte_end < c.dte_end THEN a.dte_end ELSE c.dte_end END) dte_end
    FROM   T_RE_SPEND_MET a,
           t_re_base b,
           t_re_elig c,
           t_pub_hlth_pgm d
    WHERE  a.dte_last_updated >= :last_nemt_cyc_dte_1
    AND    a.dte_last_updated <= :last_nemt_cyc_dte_2
    AND    a.dte_end          >= :POLICY_START_DATE
    AND    a.sak_case          = b.sak_case
    AND    b.sak_recip         = c.sak_recip
    AND    c.dte_effective    <= a.dte_end
    AND    c.dte_end          >= a.dte_effective
    AND    c.cde_status1      != 'H'
    AND    c.sak_pub_hlth      = d.sak_pub_hlth
    AND    d.cde_pgm_health   IN ('TXIX','TXXI','MKN','MN','P19','P21')
    AND    NOT EXISTS (SELECT 1
                       FROM   t_mc_re_nemt g
                       WHERE  g.sak_recip = b.sak_recip)
order by sak_recip, dte_effective;

/*********************************************************************/
/* CURSOR NAME: pmp_assign_cursor                                    */
/* DESCRIPTION: Get all NT assignments (NEMT) for a beneficiary      */
/*              that end after the start of the benfit month.        */
/*********************************************************************/
EXEC SQL DECLARE pmp_assign_cursor CURSOR FOR
SELECT
    assign.sak_recip,
    assign.sak_re_pmp_assign,
    assign.sak_pgm_elig,
    elig.sak_pub_hlth,
    elig.dte_effective,
    elig.dte_end,
    assign.sak_pmp_ser_loc,
    assign.cde_rsn_mc_start
FROM
    t_re_elig elig,
    t_re_pmp_assign assign
WHERE
    elig.sak_recip = :sql_pntl_rcd.sak_recip
AND elig.dte_end >= :sql_dte_end
AND elig.cde_status1 <> 'H'
AND elig.sak_pub_hlth   = :sak_pub_hlth_nemt
AND assign.sak_recip    = elig.sak_recip
AND assign.sak_pgm_elig = elig.sak_pgm_elig;

/*********************************************************************/
/*                       Function Prototypes                         */
/*********************************************************************/
static void initialize(int, char *[]);
static void finalize(void);
static int ConnectToDB(void);
static int fetch_pot_info(void);
static void display_rcd_cnt(void);
static void sqlerror(void);
static void get_assign_dates(void);
static int validate_cur_assigns(void);
static void ROLLBACK(void);
static void check_rc(struct mc_rtn_message *rtn_msg_str_ptr, int parm_rc, char *FNC);
static int det_pgm_assign_dte(int parm_sak_pub_hlth);
static int fetch_default_pmp_cursor(void);
static int get_next_sak(char parm_sak_name[18+1]);
static int  is_pgm_autoassignable(int parm_sak_pub_hlth);
static void get_recip_info(void);
static void update_pmp_panel_size(int parm_sak_pmp_ser_loc);
static void lock_recip_base(void);
static void process_cmd_line(int, char *[] );
static void print_usage(void);
static void save_to_db(char FNC[30]);
static void do_hw19hck_assign(int);
static int get_pmp_sak(int parm_sak_pub_hlth, int parm_date , char parm_ind_focus[1+1]);
static int do_default_assign(int, int, int);
static void update_pmp_autoassgn(int parm_sak_pmp_ser_loc, int parm_dte);
static int  valid_pmp_assign(struct valid_pmp_info_type *, int, int, int , int);
static int  check_pgm_elig(int , int );
static void display_pmp_info(int parm_sak_pmp_ser_loc, int parm_date);
static int get_sysparm(const char *parm_nam_program);
static void write_rpt_tbl(int cde_rsn);
static void process_death(void);
static void check_cur_assign(int, int);
static int fetch_assign(void);
static int get_sak_cde_aid(int, int);
static int get_sak_pgm_elig(void);
static int verify_aid_elig(void);
static int new_aid_elig_seg(int parm_dte_eff);
static int history_aid_seg(void);
static int get_sak_cde_aid(int, int);
static int end_date_aid_elig(int parm_dte_end );
static int make_pmp_assgn (int parm_pmp_svc_loc,  char *parm_assgn_rsn,    int parm_ent_autoassign,
                           int parm_assgn_dte,    int  parm_assgn_end_dte, int parm_sak_pub_hlth,
                           int parm_sak_prov_mbr, char *parm_cde_svc_loc_mbr );

/****************************************************************************/
/*                                                                          */
/* Function:    main()                                                      */
/*                                                                          */
/* Description: Controls the overall program logic.                         */
/*                                                                          */
/* Date       CO    SE                Description                           */
/* ---------- ----- ----------------- --------------------------------      */
/* 09/01/2009 11976 Nirmal Thiagarajan Initial Creation                     */
/****************************************************************************/
int main(int argc, char *argv[])
{
    char FNC[] = "main()";
    int cursor_rc = 0;
    int rc_elig = 0;
    int rc_new_elig = mgd_FALSE;
    int rc_sed_recip = mgd_FALSE;
    int continue_process = mgd_TRUE;
    int pmp_avail_cnt = 0;
    int rc_pmp_cnt = 0;
    int rc1 = 0;

    int sak_pmp_ser_loc;
    char tmp_type[10];

    /* Flags used to control logic flow */
    int messaging = mgd_TRUE;
    int incl_specified_pgm = mgd_TRUE;

    /* Common Function structures */
    struct mc_is_reason rtn_reason_str;
    struct mc_rtn_message rtn_msg_str;
    struct mc_eligible mc_is_sed_loc;

    initialize(argc, argv);

    cursor_rc = fetch_pot_info();

    mc_debug_level = 1;

    while (cursor_rc == ANSI_SQL_OK)
    {
        display_rcd_cnt();

        benefit_mth_start = sql_pntl_rcd.dte_effective;
	if (benefit_mth_end == 0)
        	benefit_mth_end   = c_get_month_end(benefit_mth_start);
        benefit_mth_prev_end_dte = c_get_month_end(c_get_month_begin_prev(benefit_mth_start));

        /* get the recip's ID, gender, age, etc... */
        if (prev_sak_recip != sql_pntl_rcd.sak_recip)
           get_recip_info();

        if (arg_rsn_rpting == mgd_TRUE)
        {
          fprintf(err_rpt_file_ptr, "\n\tprocessing sak_recip [%d], BID [%s] for benefit month [%d]\n",
                                    sql_pntl_rcd.sak_recip, recip_id_medicaid, benefit_mth_start);
        }

        enrolled_nemt     = mgd_FALSE;
        eligible_for_nemt = mgd_FALSE;
        continue_process  = mgd_TRUE;

        if ((recip_dte_death != 0) && (benefit_mth_start > recip_dte_death))
        {
           continue_process = mgd_FALSE; /* Benefit month is after DOD so do not conitnue */
        }

        /* process MONTHLY records */
        if ((!RETRO_ELIG_MONTH) &&
            (arg_run_level == MONTHLY) &&
            (continue_process == mgd_TRUE))
        {
           if (arg_rsn_rpting == mgd_TRUE)
           {
              fprintf(err_rpt_file_ptr, "\tbenefit month is CURRENT\n");
           }

           /* check for changes to existing assignments */
           rc_elig = validate_cur_assigns();
           if (rc_elig != FUNCTION_SUCCESSFUL)
           {
              c_do_display_rtn_msg(&rtn_msg_str, FNC);
              ROLLBACK();
              ind_abend = mgd_TRUE;
              finalize();
              exit(FAILURE);
           }

           /* when no current assign exists or the current assign was ended since it is not valid */
           if (enrolled_nemt == mgd_FALSE)
           {
               rc_elig = check_pgm_elig(sak_pub_hlth_nemt,benefit_mth_start);
               if ( rc_elig == mgd_TRUE )
                   eligible_for_nemt = mgd_TRUE;
               else
                   eligible_for_nemt = mgd_FALSE;

               if ((eligible_for_nemt) && (autoassign_nemt))
                  enrolled_nemt = do_default_assign(benefit_mth_start, EOT, sak_pub_hlth_nemt);
               else
                  cnt_nemt_notelig++;
           }
           else
           {
               cnt_nemt_continued++;
           }

        }
        else /* Daily records - Only virgin eligibles/SED LOC benes will be processed */
        {
            if (arg_rsn_rpting == mgd_TRUE)
            {
              fprintf(err_rpt_file_ptr, "\tbenefit month is RETRO\n");
            }

            rc_elig = check_pgm_elig(sak_pub_hlth_nemt,benefit_mth_start);
            if ( rc_elig == mgd_TRUE )
                eligible_for_nemt = mgd_TRUE;
            else
                eligible_for_nemt = mgd_FALSE;

            if (eligible_for_nemt)
            {
               check_cur_assign(sak_pub_hlth_nemt, benefit_mth_start);

               if ((!enrolled_nemt) && (autoassign_nemt))
                  enrolled_nemt = do_default_assign(benefit_mth_start,
                                                    ((benefit_mth_start == last_kaecses_elig_dte)?EOT:benefit_mth_end),
                                                    sak_pub_hlth_nemt);
               else
               {
                  cnt_nemt_continued++;
                  if (arg_rsn_rpting == mgd_TRUE)
                  {
                     fprintf(err_rpt_file_ptr, "\t\t\tAlready Enrolled in pgm [%d]\n",sak_pub_hlth_nemt);
                  }
               }
            }
            else
                cnt_nemt_notelig++;

        }
        process_death();

        /* COMMIT or ROLLBACK the changes depending on the command line parm... */
        save_to_db(FNC);
        cursor_rc = fetch_pot_info();

    }  /* end of fetch cursor while loop */

    ind_abend = mgd_FALSE;
    finalize();

    /* Perform a final commit or full rollback, depending on the command line parm... */
    if (arg_save_to_db == mgd_TRUE)
    {
        strcpy(tmp_type, "COMMIT");
        EXEC SQL COMMIT;
    }
    else
    {
        strcpy(tmp_type, "ROLLBACK");
        EXEC SQL ROLLBACK;
    }

    if (sqlca.sqlcode != 0)
    {
        fprintf(stderr, "DATABASE ERROR in %s:  %s FAILED for "
               "sak_recip [%d], sak_pub_hlth [%d]\n", FNC, tmp_type, sql_pntl_rcd.sak_recip,
               sql_pntl_rcd.sak_pub_hlth);
        sqlerror();
        exit(FAILURE);
    }

    exit(0);
}
/****************************************************************************/
/*                                                                          */
/* Function:  display_rcd_cnt()                                             */
/*                                                                          */
/* Description:  Display the number of records processed.                   */
/*                                                                          */
/* Date       CO    SE              Description                             */
/* ---------- ----- --------------- --------------------------------        */
/*                                                                          */
/****************************************************************************/
static void display_rcd_cnt(void)
{
    /* Display some counts so we can tell how far along we are... */
    if ((cnt_potential_read == 1) ||
        (cnt_potential_read == 10) ||
        (cnt_potential_read == 20) ||
        (cnt_potential_read == 100) ||
        ((cnt_potential_read > 0) && (cnt_potential_read % 500) == 0))
    {
        fprintf(stderr, "\n\tCount of Potential records read ....................... [%9d]\n", cnt_potential_read);
        fprintf(stderr, "\t\t# of NEMT Assignments Created ................. [%9d]\n", cnt_nemt_assign);
        fprintf(stderr, "\t\t# of NEMT Assignments Extended ................ [%9d]\n", cnt_nemt_extend);
        fprintf(stderr, "\t\t# of NEMT Assignments Reported ................ [%9d]\n", cnt_nemt_report);
        fprintf(stderr, "\t\t# of NEMT Assignments Not Eligible ............ [%9d]\n", cnt_nemt_notelig);
        fprintf(stderr, "\t\t# of NEMT Assignments Continued ............... [%9d]\n", cnt_nemt_continued);
        fflush(stderr);
    }
}
/*********************************************************************/
/*                                                                   */
/* Function:    initialize()                                         */
/*                                                                   */
/* Description: Retrieve environment variables, open files,          */
/*              connect to database, retrieve APPappropriate dates,     */
/*              open main cursor.                                    */
/*                                                                   */
/* Date       CO    SE              Description                      */
/* ---------- ----- --------------- ------------------------------   */
/*********************************************************************/
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
    int   first_of_next_month = 0;

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

    if ((rc = ConnectToDB()) == FAILURE)
        exit(FAILURE);

    /* Retrieve the current time... */
    rc = c_get_dte_current(&mc_dates_str, &rtn_msg_str);

    if ( rc == FUNCTION_SUCCESSFUL )
    {
         sprintf(curr_date, "%d", mc_dates_str.dte);
         sprintf(curr_time, "%04d", mc_dates_str.time_hhmm);
         first_of_next_month = mc_dates_str.fom_next;
    }
    else
    {
         c_do_display_rtn_msg( &rtn_msg_str, FNC);
         exit(FUNCTION_ERROR);
    }

    get_assign_dates();

    /************************/
    /* Open the main cursor */
    /************************/
    EXEC SQL open all_potentials;

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "   ERROR: could not open the all_potentials Cursor\n");
        sqlerror();
        exit(FAILURE);
    }

    /* Retrieve the sak_elig_stop to use for Managed Care segments...  */
    rc = c_get_elig_stop_rsn(&elig_stop_str, &rtn_msg_str, 0, "   ");
    check_rc(&rtn_msg_str, rc, FNC);

    mc_sak_elig_stop = elig_stop_str.sak_elig_stop;

    /* Get all the autoassign entities */
    rc = c_get_mc_entity(&entity_str, &rtn_msg_str, "NMT");
    check_rc(&rtn_msg_str, rc, FNC);
    SAK_ENT_NEMT_AUTOASSIGN = entity_str.sak_mc_entity;

    if (arg_run_level == MONTHLY)
    {
        if (!c_is_on_same_month(dte_enroll_nemt, first_of_next_month))
        {
            fprintf(stderr, "\n\nNEMT Enrollment Date [%d] IS NOT CORRECT!!!  "
                            "It should be set to [%d]!!!  T_SYSTEM_PARMS MAY NEED TO BE UPDATED.\n\n\n",
                dte_enroll_nemt, first_of_next_month);
            exit(FAILURE);
        }
    }

    if (arg_save_to_db == mgd_TRUE)
        fprintf(err_rpt_file_ptr, "\n*** Updates WILL be saved to the database ***\n\n");
    else
        fprintf(err_rpt_file_ptr, "\n*** TEST MODE:  Updates will NOT be saved to the database!!! ***\n\n");

    fprintf(err_rpt_file_ptr, "\n*************************************************************\n");
    fprintf(err_rpt_file_ptr, "              BEGIN PROCESSING OF POTENTIAL TABLE");
    fprintf(err_rpt_file_ptr, "\n*************************************************************\n");

    fprintf(stderr, "\nmc_sak_elig_stop = [ %d ]\n", mc_sak_elig_stop);
    fprintf(stderr, "SAK_ENT_NEMT_AUTOASSIGN = [ %d ]\n", SAK_ENT_NEMT_AUTOASSIGN);

    fflush(stderr);
    fflush(err_rpt_file_ptr);
}

/*********************************************************************/
/*                                                                   */
/* Function:     fetch_pot_info()                                    */
/*                                                                   */
/* Description:  Retrieve the next Potential record to process.      */
/*                                                                   */
/*********************************************************************/
static int fetch_pot_info(void)
{
    EXEC SQL FETCH all_potentials
           INTO  :sql_pntl_rcd.sak_recip,
                 :sql_pntl_rcd.dte_effective,
                 :sak_pub_hlth_mcaid,
                 :benefit_mth_end;

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

/*********************************************************************/
/*                                                                   */
/* Function:     get_recip_info()                                    */
/*                                                                   */
/* Description:  Retrieve additional information for the beneficiary */
/*                                                                   */
/*                                                                   */
/*********************************************************************/
static void get_recip_info(void)
{
    int rc;
    char FNC[] = "get_recip_info()";

    EXEC SQL SELECT id_medicaid,
                    sak_case,
                    cde_county,
                    adr_zip_code,
                    dte_birth,
                    dte_death
             INTO   :recip_id_medicaid,
                    :recip_sak_case,
                    :recip_cde_county,
                    :recip_zip_code,
                    :recip_dte_birth,
                    :recip_dte_death
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

    prev_sak_recip = sql_pntl_rcd.sak_recip;
    return;

}

/*********************************************************************/
/*                                                                   */
/* Function:     det_pgm_assign_dte()                                */
/*                                                                   */
/* Description:  Determine the assignment date for the specified pgm */
/*                                                                   */
/*********************************************************************/
static int det_pgm_assign_dte(int parm_sak_pub_hlth)
{
    if (parm_sak_pub_hlth == sak_pub_hlth_nemt)
    {
        strcpy(recip_cde_pgm_health, "NEMT");
        /* return(dte_enroll_nemt); */
        return 0;
    }
    else
    {
        strcpy(recip_cde_pgm_health, "   ");
        /* return(dte_current); */
        return 0;
    }
}

/******************************************************************************/
/*                                                                            */
/* Function:    finalize()                                                    */
/*                                                                            */
/* Description: Close files and display summary information                   */
/*                                                                            */
/* SE               CO    Date      Description                               */
/* ---------------- ----- --------  ------------------------------------      */
/******************************************************************************/
static void finalize(void)
{
    char final_status[50];

    fprintf(stderr, "\n\n");
    fprintf(stderr, "\tNumber of Potential records read ................... [%9d]\n", cnt_potential_read);
    fprintf(stderr, "\t\t# of NEMT Assignments Created .............. [%9d]\n", cnt_nemt_assign);
    fprintf(stderr, "\t\t# of NEMT Assignments Extended ............. [%9d]\n", cnt_nemt_extend);
    fprintf(stderr, "\t\t# of NEMT Assignments Reported ............. [%9d]\n", cnt_nemt_report);
    fprintf(stderr, "\t\t# of NEMT Assignments Not Eligible ......... [%9d]\n", cnt_nemt_notelig);
    fprintf(stderr, "\t\t# of NEMT Assignments Continued ............ [%9d]\n", cnt_nemt_continued);

    fprintf(stderr, "\n");

    if (ind_abend == mgd_TRUE)
        strcpy(final_status, "Abnormal End of Process");
    else
        strcpy(final_status, "Process Successfully Completed");

    fprintf(err_rpt_file_ptr, "\n\n*************************************************************\n");
    fprintf(err_rpt_file_ptr, "                END PROCESSING OF POTENTIAL TABLE\n");
    fprintf(err_rpt_file_ptr, "       FINAL STATUS is:  [%s]", final_status);
    fprintf(err_rpt_file_ptr, "\n*************************************************************\n\n");

    fprintf(err_rpt_file_ptr, "\n\n");
    fprintf(err_rpt_file_ptr, "Number of Potential records read ...................... [%9d]\n", cnt_potential_read);
    fprintf(err_rpt_file_ptr, "\t# of NEMT Assignments Created ................. [%9d]\n", cnt_nemt_assign);
    fprintf(err_rpt_file_ptr, "\t# of NEMT Assignments Extended ................ [%9d]\n", cnt_nemt_extend);
    fprintf(err_rpt_file_ptr, "\t# of NEMT Assignments Reported ................ [%9d]\n", cnt_nemt_report);
    fprintf(err_rpt_file_ptr, "\t# of NEMT Assignments Not Eligible ............ [%9d]\n", cnt_nemt_notelig);
    fprintf(err_rpt_file_ptr, "\t# of NEMT Assignments Continued ............... [%9d]\n", cnt_nemt_continued);

    fprintf(err_rpt_file_ptr, "\n");

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

/************************************************************************/
/*                                                                      */
/* Function Name:  process_cmd_line()                                   */
/*                                                                      */
/* Description: This function reads in the arguments from the command   */
/*              line and parses the parameters.                         */
/*                                                                      */
/************************************************************************/
static void process_cmd_line(int argnum, char *arglst[] )
{
    int argcnt = 1;
    char temp_string[MAX_STRING+1];
    short int found_save_to_db = mgd_FALSE;
    short int found_runlevel = mgd_FALSE;
    short int found_rsn_reporting = mgd_FALSE;


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
    found_runlevel = mgd_TRUE;
    found_rsn_reporting = mgd_TRUE;
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
/************************************************************************/
/*                                                                      */
/* Function Name:  print_usage()                                        */
/*                                                                      */
/* Description:  Prints out a usage message if the was an error parsing */
/*               the command line parameters.                           */
/*                                                                      */
/*                                                                      */
/************************************************************************/
static void print_usage(void)
{
     fprintf(stderr,"\n\n%s -s SAVE_TO_DB -l DAILY | MONTHLY\n",PROGNAME);
     fputs  ("    Where:\n",stderr);
     fputs  ("       SAVE_TO_DB       - is either 'true' or 'false'\n",stderr);
     fputs  ("       DAILY | MONTHLY  - run daily or monthly\n",stderr);
     fputs  ("\n\n",stderr);
}

/******************************************************************************/
/*                                                                            */
/* Function:     get_assign_dates()                                           */
/*                                                                            */
/* Description:  This function gets various dates from the database.          */
/*                                                                            */
/*                                                                            */
/* SE               CO    Date      Description                               */
/* ---------------- ----- --------  ------------------------------------      */
/******************************************************************************/
static void get_assign_dates(void)
{
    char FNC[] = "get_assign_dates()";
    int rc;
    char ind_focus[1+1] = "\0";
    struct mc_dte enroll_dte_str;
    struct mc_rtn_message rtn_msg_str;
    struct mc_pub_hlth_pgm hlth_pgm_str;

    /* The max date the beneficiary can be assigned retro actively */
    EXEC SQL SELECT to_number(to_char(last_day(add_months(SYSDATE, -DATE_PARM_1)) +1, 'YYYYMMDD')),
                    to_number(to_char(sysdate, 'YYYYMMDD'))
               INTO :mc_retro_date,
                    :dte_current
               FROM t_system_parms
              WHERE nam_program = 'MGDNMRET';

    if ( sqlca.sqlcode != 0 )
    {
        fprintf(stderr, "Failed to retrieve retro date using parm MGDNMRET. \n");
        sqlerror();
        exit(-1);
    }

     /* Last KAECSES run date */
    rc = get_sysparm("KCSESLRM");
    if (rc == FUNCTION_SUCCESSFUL)
    {
        last_kaecses_elig_dte  = sql_dte_parm_2;
    }
    else
        exit(-1);

     /* managed care assignment end date, never used. */
/*  rc = get_sysparm("MGDASMED");
    if (rc == FUNCTION_SUCCESSFUL)
        mc_assn_end_dte = sql_date_parm_1;
    else
        exit(-1);
*/
    /* cycle run date */
    rc = get_sysparm("AIMCYCLE");
    if (rc == FUNCTION_SUCCESSFUL)
        aim_cycle_date = sql_dte_parm_2;
    else
        exit(-1);

    /* Last NEMT cycle run date */
    rc = get_sysparm("MGDNMCYC");
    if (rc == FUNCTION_SUCCESSFUL)
    {
        last_nemt_cyc_dte_1 = sql_date_parm_1;
        last_nemt_cyc_dte_2 = sql_dte_parm_2;
    }
    else
        exit(-1);

    /* KanCare initial date.   CO14303 */
    rc = get_sysparm("MGDKCINT");
    if (rc == FUNCTION_SUCCESSFUL)
    {
        date_kcint  = sql_date_parm_1;
    }
    else
        exit(-1);

    /* HealthWave End date.    CO14303 */
    rc = get_sysparm("MGDHWEND");
    if (rc == FUNCTION_SUCCESSFUL)
    {
        date_hwend  = sql_date_parm_1;
    }
    else
        exit(-1);

    /********************** NEMT Program Data ************************* */
    rc = c_get_pub_hlth_pgm(&hlth_pgm_str, &rtn_msg_str, 0, "NEMT");
    check_rc(&rtn_msg_str, rc, FNC);
    sak_pub_hlth_nemt = hlth_pgm_str.sak_pub_hlth;

    rc = c_get_dte_enroll(&enroll_dte_str, &rtn_msg_str, sak_pub_hlth_nemt);
    check_rc(&rtn_msg_str, rc, FNC);
    dte_enroll_nemt = enroll_dte_str.dte;

    /* Determine if Autoassignment is 'on' for the NEMT program... */
    autoassign_nemt = is_pgm_autoassignable(sak_pub_hlth_nemt);

    /* Determine the sak-pmp_svc_loc for NEMT with cap */
    strncpy(ind_focus,"Y",1);
    sak_pmp_svc_nemt_cap = get_pmp_sak(sak_pub_hlth_nemt,dte_current,ind_focus);

    /* Determine the sak-pmp_svc_loc for NEMT with no cap */
    strncpy(ind_focus,"N",1);
/*
    sak_pmp_svc_nemt_nocap = get_pmp_sak(sak_pub_hlth_nemt,dte_current,ind_focus);
*/
    sak_pmp_svc_nemt_nocap = -1;


    /***************************************************************************************/

    fprintf(stderr, "\n");
    fprintf(stderr, "Current date           = [ %d ]\n", dte_current);

    fprintf(err_rpt_file_ptr, "*************************************************************\n");
    fprintf(err_rpt_file_ptr, " NEMT Autoassignment Log for Date [%d] 24 hr Time [%s]\n", dte_current, curr_time);
    fprintf(err_rpt_file_ptr, "*************************************************************\n");

    fprintf(stderr, "NEMT normal enrollment date   = [ %d ]  MGDENRNM \n", dte_enroll_nemt);
    fprintf(stderr, "Kancare initial date          = [ %d ]  MGDKCINT \n", date_kcint);       /* CO14303 */
    fprintf(stderr, "Kancare HW End  date          = [ %d ]  MGDHWEND \n", date_hwend);

    fprintf(err_rpt_file_ptr, "NEMT normal enrollment date  = [ %d ]\n", dte_enroll_nemt);
    fprintf(err_rpt_file_ptr, "Kancare initial date         = [ %d ]  MGDKCINT \n", date_kcint);  /* CO14303 */
    fprintf(err_rpt_file_ptr, "Kancare HW End  date         = [ %d ]  MGDHWEND \n", date_hwend);

    fprintf(stderr, "sak_pub_hlth_nemt  = [ %d ]\n", sak_pub_hlth_nemt);

    fprintf(stderr, "sak_pmp_svc_nemt_cap   = [ %d ]\n", sak_pmp_svc_nemt_cap);
    fprintf(stderr, "sak_pmp_svc_nemt_nocap = [ %d ]\n", sak_pmp_svc_nemt_nocap);

    fprintf(stderr, "autoassign_nemt = [ %d ]\n", autoassign_nemt);

}

/*********************************************************************/
/*                                                                   */
/* Function:    ConnectToDB()                                        */
/*                                                                   */
/* Description: Connect to the database.                             */
/*                                                                   */
/*********************************************************************/
/*                 Modification Log:                                 */
/* Date     CO       SE         Description                          */
/* -------- -------- ---------- ------------------------------------ */
/*********************************************************************/
static int ConnectToDB(void)
{
    int lConnectRtn;
    int lConnectRtn3;
    char FNC[] = "ConnectToDB()";

    lConnectRtn  = ConnectDB();
    lConnectRtn3 = ConnectDB3();

    if (lConnectRtn3 != ANSI_SQL_OK)
    {
        fprintf(stderr,"%s: ERROR- SQL DBCONN3 Connect Failed SQL Code [%8d]\n",
                FNC, lConnectRtn3);
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

/*********************************************************************/
/*                                                                   */
/* Function:     get_next_sak()                                      */
/*                                                                   */
/* Description:  Retrieve the next system key to use for the         */
/*               specified sak_name and commit the changes           */
/*                                                                   */
/*                                                                   */
/*********************************************************************/
static int get_next_sak(char parm_sak_name[18+1])
{
    EXEC SQL BEGIN DECLARE SECTION;
        char sql_sak_name[18+1];
        int sql_sak;
    EXEC SQL END DECLARE SECTION;

    strcpy(sql_sak_name, parm_sak_name);

    if (arg_save_to_db == mgd_TRUE)
    {
        EXEC SQL SELECT  sak
                 INTO    :sql_sak
                 FROM    t_system_keys
                 WHERE   sak_name = :sql_sak_name
             for UPDATE;
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
            if (arg_save_to_db == mgd_TRUE)
            {
                EXEC SQL COMMIT;
                if (sqlca.sqlcode != 0)
                {
                    fprintf(stderr, " DATABASE ERROR:\n"
                           "    COMMIT of SAK value FAILED for "
                           "sak_name: [%s]\n", parm_sak_name);
                    sqlerror();
                    ind_abend = mgd_TRUE;
                    finalize();
                    exit(-1);
                }
            }
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

/*********************************************************************/
/*                                                                   */
/* Function:     lock_recip_base()                                   */
/*                                                                   */
/* Description:  This function locks the beneficiary's base record   */
/*               while we are performing an insert of the PMP        */
/*               assignment so that Claims will always process with  */
/*               the most recent information.                        */
/*                                                                   */
/*********************************************************************/
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

/*********************************************************************/
/*                                                                   */
/* Function:     is_pgm_autoassignable()                             */
/*                                                                   */
/* Description:  This function determines if Autoassignment is 'on'  */
/*               for each Managed Care program.                      */
/*                                                                   */
/*********************************************************************/
static int is_pgm_autoassignable(int parm_sak_pub_hlth)
{
    EXEC SQL BEGIN DECLARE SECTION;
        char sql_ind_mc_autoassign[1+1];
        int sql_sak_pub_hlth;
        int sql_sak_prov_pgm;
    EXEC SQL END DECLARE SECTION;

    char FNC[] = "is_pgm_autoassignable";

    sql_sak_pub_hlth = parm_sak_pub_hlth;

    EXEC SQL SELECT ind_mc_autoassign
               INTO :sql_ind_mc_autoassign
               FROM t_pr_covered_pgm c,
                    t_pr_enroll_pgm e
              WHERE c.sak_prov_pgm = e.sak_prov_pgm AND
                    e.cde_prov_pgm IN ('NEMT') AND
                    c.sak_pub_hlth = :sql_sak_pub_hlth;

    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: could not retrieve the autoassign indicator "
                "for sak_pub_hlth [%d]\n", FNC,sql_sak_pub_hlth);
        sqlerror();
        exit(FAILURE);
    }

    if (sql_ind_mc_autoassign[0] == 'Y')
        return(mgd_TRUE);
    else
        return(mgd_FALSE);

}

/*********************************************************************/
/*                                                                   */
/* Function:     check_rc()                                          */
/*                                                                   */
/* Description:  Check the return code form the common function      */
/*                                                                   */
/*********************************************************************/
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

/*********************************************************************/
/*                                                                   */
/* Function:     sqlerror()                                          */
/*                                                                   */
/* Description:  Displays troubleshooting information from Oracle    */
/*               when an error has occured.                          */
/*                                                                   */
/*********************************************************************/
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

/*********************************************************************/
/*                                                                   */
/* Function:     save_to_db()                                        */
/*                                                                   */
/* Description:  Commits the changes to the database                 */
/*                                                                   */
/*********************************************************************/
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

/*********************************************************************/
/*                                                                   */
/* Function:     rollback()                                          */
/*                                                                   */
/* Description:  Rolls back the database changes.                    */
/*                                                                   */
/*********************************************************************/
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

/*********************************************************************/
/*                                                                   */
/* Function:     get_pmp_sak                                         */
/*                                                                   */
/* Description:  Retrieves the sak_pmp_svc_loc for a given  program  */
/*                                                                   */
/* Modification Log:                                                 */
/*                                                                   */
/* Request Author       Date          Comments                       */
/* ------- ------------ ------------- -------------------------------*/
/*                                                                   */
/*********************************************************************/
static int get_pmp_sak(int parm_sak_pub_hlth, int parm_date , char parm_ind_focus[1+1])
{
   char FNC[] = "get_pmp_sak()";

   EXEC SQL BEGIN DECLARE SECTION;
      int    sql_sak_pub_hlth;
      int    sql_sak_pmp_svc_loc;
      int    sql_date;
      char    sql_ind_cap[1+1] = "\0";
   EXEC SQL END DECLARE SECTION;

   sql_sak_pub_hlth = parm_sak_pub_hlth;
   sql_date = parm_date;
   strncpy(sql_ind_cap, parm_ind_focus, 1);

   /* select pmp service location for a given program that was enrolled
      on a given date with atleast one county */
   EXEC SQL  SELECT  distinct pmp.sak_pmp_ser_loc
               INTO  :sql_sak_pmp_svc_loc
               FROM  t_pmp_svc_loc pmp,
                     t_mc_pmp_enrl_cnty cnty,
                     t_mc_pmp_focus_xrf fxrf
              WHERE   pmp.sak_pmp_ser_loc = cnty.sak_pmp_ser_loc
                AND   pmp.sak_pub_hlth    = :sql_sak_pub_hlth
                AND   pmp.dte_effective  <= :sql_date
                AND   pmp.dte_end        >= :sql_date
                AND   cnty.dte_effective <= :sql_date
                AND   cnty.dte_end       >= :sql_date
                AND   cnty.ind_autoassign = 'Y'
                AND   cnty.ind_panel_hold = 'N'
               // AND   cnty.cde_enroll_status = 'E'
                AND   fxrf.sak_pmp_focus = pmp.sak_pmp_focus
                AND   fxrf.dte_effective <= :sql_date
                AND   fxrf.dte_end       >= :sql_date
                AND   fxrf.ind_capitation = :sql_ind_cap;

   if (sqlca.sqlcode != ANSI_SQL_OK )
   {
       fprintf (stderr, "%s - ERROR: could not retrieve T_PMP_SVC_LOC record for "
               "sak_pub_hlth [%d] sql_ind_cap [%s] on [%d] \n",
               FNC, parm_sak_pub_hlth,sql_ind_cap,sql_date);
       sqlerror();
       ind_abend = mgd_TRUE;
       finalize();
       exit(FAILURE);
   }

   return(sql_sak_pmp_svc_loc);
}
/******************************************************************************/
/*                                                                            */
/* Function:     do_default_assign()                                          */
/*                                                                            */
/* Description:  Performs the default assignment logic.                        */
/*                                                                            */
/* Date       CO     SE             Description                               */
/* ---------- ------ -------------- --------------------------------------    */
/*                                                                            */
/******************************************************************************/
static int do_default_assign(int parm_date, int parm_end_date, int parm_sak_pub_hlth)
{
   char FNC[] = "do_default_assign()";
   int rc_xtend_assign = FUNCTION_FAILED;
   int messaging_on = mgd_TRUE;
   int assigned = mgd_FALSE;
   int sak_assign_grp = -1;
   int parm_sak_pmp_svc_loc = -1;

/*
   if (noncap_svc_loc == mgd_TRUE)
   {
        parm_sak_pmp_svc_loc = sak_pmp_svc_nemt_nocap;
   }
   else
   {
        parm_sak_pmp_svc_loc = sak_pmp_svc_nemt_cap;
   }
*/
     parm_sak_pmp_svc_loc = sak_pmp_svc_nemt_cap;
                                                            /* CO14303 */
     if (aim_cycle_date >= date_kcint)
     {
        fprintf (err_rpt_file_ptr, "\t\t... parm_date: %d  parm_end_date: %d  date_kcint: %d  date_hwend: %d \n",
                 parm_date, parm_end_date, date_kcint, date_hwend);
        if (parm_date > date_hwend)
        {
           fprintf(err_rpt_file_ptr, "\t\tDate is after the program end date. sak_recip [%d] , BID [%s], pgm [%s] No assignment made. \n",
                        sql_pntl_rcd.sak_recip, recip_id_medicaid, recip_cde_pgm_health);
           return (mgd_TRUE);
        }
        if (parm_end_date > date_hwend)
        {
           fprintf(err_rpt_file_ptr, "\t\tEnd date is changed due to KanCare. sak_recip [%d] , BID [%s], pgm [%s]  Date was %d, now %d .\n",
                        sql_pntl_rcd.sak_recip, recip_id_medicaid, recip_cde_pgm_health, parm_end_date, date_hwend);
           parm_end_date = date_hwend;
        }
     }

     rc_xtend_assign = c_do_extend_assign(&mc_rtn_msg_str,
                                           sql_pntl_rcd.sak_recip,
                                           parm_sak_pub_hlth,
                                           parm_date,
                                           parm_end_date,
                                           parm_sak_pmp_svc_loc,
                                           recip_sak_cde_aid);

     if (rc_xtend_assign == FUNCTION_SUCCESSFUL)
     {
         assigned = mgd_TRUE;
         cnt_nemt_extend++;

         if (arg_rsn_rpting == mgd_TRUE)
         {
           fprintf(err_rpt_file_ptr, "\t\tBene has assign with pgm [%s] for prev mth, and the assign was extended\n",
                        recip_cde_pgm_health);
         }
         return(assigned);
     }
     else if (rc_xtend_assign == FUNCTION_ERROR)
     {
          fprintf(stdout, "\n Unable to extend the previous assignment for pgm [%s]\n",
                        recip_cde_pgm_health);
          c_do_display_rtn_msg(&mc_rtn_msg_str, FNC);
          ROLLBACK();
          ind_abend = mgd_TRUE;
          finalize();
          exit(FAILURE);
     }

     /* rc_xtend_assign == FUNCTION_FAILED  is good condition i.e prev mth assignment not found so make assign */


   make_pmp_assgn(parm_sak_pmp_svc_loc,
                  DEFAULT_NEMT_ASSIGN,
                  SAK_ENT_NEMT_AUTOASSIGN,
                  parm_date, parm_end_date,
                  parm_sak_pub_hlth,  0, " ");


   /*
      sak_assign_grp = get_next_sak("SAK_ASSIGN_GRP");
      add_mc_assign_grp(sak_assign_grp);
   */

   assigned = mgd_TRUE;

   return(assigned);

}

/******************************************************************************/
/*                                                                            */
/* Function:     check_cur_assign                                             */
/*                                                                            */
/* Description:  This function will check if the beneficairy is enrolled in a */
/*               program with right pmp svc location                          */
/*                                                                            */
/* Date       CO     SE             Description                               */
/* ---------- ------ -------------- --------------------------------------    */
/*                                                                            */
/******************************************************************************/
static void check_cur_assign(int parm_sak_pub_hlth, int parm_date)
{
   char FNC[] = "check_cur_assign()";
   int messaging_on = mgd_TRUE;
   int rc = 0;

   struct mc_eligible rtn_data_str;
   struct mc_rtn_message rtn_msg_str;
   struct mc_aid_elig sql_str;

   if (arg_rsn_rpting == mgd_TRUE)
   {
      fprintf(err_rpt_file_ptr, "\t\tcheck if bene already has assignment..... ");
   }

   rc = c_is_already_enrolled(&rtn_data_str, &rtn_msg_str, sql_pntl_rcd.sak_recip,
                              parm_sak_pub_hlth, parm_date, mgd_TRUE);

   /* if assign exists, and the svc location matches then set the enroll flag
      if the svc loc did not matches write to error report for manual fix and set the  enroll flag */
   if (rc == mgd_TRUE)
   {
      if (arg_rsn_rpting == mgd_TRUE)
      {
        fprintf(err_rpt_file_ptr, "[YES]\n");
      }

      rc = c_get_pmp_assign_info( &assign_info_str, &rtn_msg_str, 0,
                                  sql_pntl_rcd.sak_recip, 0, parm_sak_pub_hlth, parm_date);
      check_rc(&rtn_msg_str, rc, FNC);

      /* check_pgm_elig should be called before this function as noncap_svc_loc flag is set there */
      if (noncap_svc_loc == mgd_TRUE)
      {
           if (assign_info_str.sak_pmp_ser_loc != sak_pmp_svc_nemt_nocap)
               write_rpt_tbl(CHG_SVCLOC_TO_NONCAP);

           enrolled_nemt = mgd_TRUE;
      }
      else
      {
           if (assign_info_str.sak_pmp_ser_loc != sak_pmp_svc_nemt_cap)
              write_rpt_tbl(CHG_SVCLOC_TO_CAP);

            enrolled_nemt = mgd_TRUE;
      }
   }
   else if (rc != mgd_FALSE)
   {
            check_rc(&rtn_msg_str, rc, FNC);
   }

   /* if rc = mgd_FALSE then bene does not have assignment and enrolled flags should have been set FALSE */
   if (arg_rsn_rpting == mgd_FALSE)
   {
      fprintf(err_rpt_file_ptr, "[NO ]\n");
   }

}

/*********************************************************************/
/*                                                                   */
/* Function:     make_pmp_assgn()                                    */
/*                                                                   */
/* Description:  Makes a PMP assignment for the current beneficiary. */
/*                                                                   */
/*                                                                   */
/*********************************************************************/
static int make_pmp_assgn(int parm_pmp_svc_loc, char *parm_assgn_rsn,
                          int parm_ent_autoassign, int parm_assgn_dte,
                          int parm_assgn_end_dte,
                          int parm_sak_pub_hlth, int parm_sak_prov_mbr,
                          char *parm_cde_svc_loc_mbr )
{
    char FNC[] = "make_pmp_assgn()";
    int sak_aid_elig;
    struct mc_rtn_message rtn_msg_str;
    struct mc_is_reason pmp_rsn_str;
    int rc_cfunc;
    char parm_asgn_end_rsn[2+1];

    EXEC SQL BEGIN DECLARE SECTION;
       int sql_mc_ent_del = -1;
    EXEC SQL END DECLARE SECTION;


    /* Retrieve/Update the next SAK_RE_PMP_ASSIGN... */
    sak_re_pmp_assign = get_next_sak("SAK_RE_PMP_ASSIGN");

    /* Retrieve/Update the next SAK_AID_ELIG... */
    sak_aid_elig = get_next_sak("SAK_AID_ELIG");

    /* Lock the beneficiary's base record whil we do the update (the commit will unlock it)... */
    lock_recip_base();

    strcpy(parm_asgn_end_rsn, MC_ELIG_END_REASON);
    if (parm_assgn_end_dte != EOT)
    {
        rc_cfunc = c_do_create_pmp_assign_3(&rtn_msg_str, sak_re_pmp_assign, sql_pntl_rcd.sak_recip,
                  parm_sak_pub_hlth, sak_aid_elig, recip_sak_cde_aid, parm_assgn_dte, parm_assgn_end_dte,
                  parm_pmp_svc_loc, parm_sak_prov_mbr, parm_cde_svc_loc_mbr, parm_assgn_rsn,
                  parm_asgn_end_rsn,
                  parm_ent_autoassign, parm_assgn_dte, mc_sak_elig_stop);

    }
    else
    {
        /* Note: sak_aid_elig is set by c_is_mc_eligible_2 in check_pgm_elig.  This needs to be*/
        /*  called prior to making assignment to current plan.*/
        rc_cfunc = c_do_create_pmp_assign_2(&rtn_msg_str, sak_re_pmp_assign, sql_pntl_rcd.sak_recip,
                   parm_sak_pub_hlth, sak_aid_elig, recip_sak_cde_aid, parm_assgn_dte,
                   parm_pmp_svc_loc, parm_sak_prov_mbr, parm_cde_svc_loc_mbr, parm_assgn_rsn, parm_ent_autoassign,
                   parm_assgn_dte, mc_sak_elig_stop);
    }

    check_rc(&rtn_msg_str, rc_cfunc, FNC);

    if (arg_rsn_rpting == mgd_TRUE)
    {
        fprintf(err_rpt_file_ptr, "\tCreated assignment for sak_pub_hlth [%d] on [%d]\n",
        parm_sak_pub_hlth,  parm_assgn_dte);
        display_pmp_info(parm_pmp_svc_loc, parm_assgn_dte);
    }

    /* Increment PMP's actual panel size... */
    /* increase the panel size for only open ended assignments */
    if (parm_assgn_end_dte == EOT)
        update_pmp_panel_size(parm_pmp_svc_loc);

    cnt_nemt_assign++;
    return (0);
}
/******************************************************************************/
/*                                                                            */
/* Function:     check_pgm_elig()                                             */
/*                                                                            */
/* Description:  This function will determine if the beneficiary is           */
/*               eligible for the manage care health program.                 */
/*               It will set recip_sak_cde_aid if eligible.                   */
/*                                                                            */
/* CO     Date       SE             Change                                    */
/* ------ ---------- -------------- -----------------------------             */
/*                                                                            */
/******************************************************************************/
static int check_pgm_elig(int parm_sak_pub_hlth, int parm_date)
{

    char FNC[] = "check_pgm_elig";
    int rc = mgd_FALSE;
    int incl_specified_pgm = mgd_TRUE;
    int messaging_on = mgd_TRUE;
    char daily_monthly_run = 'D';  /* daily_monthly_run is always set to 'D' for Autoassignment */
    int dte_effective = -1;
    struct mc_eligible     mc_elig_info;
    struct mc_rtn_message  mc_msg;
    struct mc_aid_elig     mc_aid_info;
    struct mc_noncap_eligible noncap_elig_info;

    det_pgm_assign_dte(parm_sak_pub_hlth);

    rc = c_is_mc_eligible_3(&mc_elig_info, &mc_msg, &mc_aid_info, &noncap_elig_info,
                          sql_pntl_rcd.sak_recip, parm_sak_pub_hlth,
                          parm_date, recip_dte_death, messaging_on, incl_specified_pgm, daily_monthly_run);

    if (rc == mgd_TRUE)
    {
        recip_sak_cde_aid = mc_aid_info.sak_cde_aid;
/*
        if (noncap_elig_info.noncap_elig_code > 0)
        {
           noncap_svc_loc = mgd_TRUE;
        }
        else
        {
           noncap_svc_loc = mgd_FALSE;
        }
*/
       noncap_svc_loc = mgd_FALSE;

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
            fprintf(err_rpt_file_ptr, "\t\tBene is NOT eligible for pgm [%d->%s] due to [%s] on [%d]\n", parm_sak_pub_hlth,
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

/************************************************************************/
/*                                                                      */
/* Function:     display_pmp_info()                                     */
/*                                                                      */
/* Description:  This function displays additional information for      */
/*               the specified PMP service location.                    */
/*                                                                      */
/*                                                                      */
/* Date       CO    SE                  Description                     */
/* ---------- ----- ------------------- ------------------------------  */
/* 09/07/2003 Defct Rob Lohrke          Added function to assist        */
/*                                      testers.                        */
/*                                                                      */
/************************************************************************/
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

    fprintf(err_rpt_file_ptr, "\t\t\tBene assigned to sak_prov [%d], location [%s], effective [%d]\n",
             pmp_str.sak_prov, pmp_str.cde_service_loc, parm_date);

    return;
}

/*********************************************************************/
/*                                                                   */
/* Function:    get_sysparm()                                        */
/*                                                                   */
/* Description: Retrieve the specified record from the system parms  */
/*              table.                                               */
/*                                                                   */
/*********************************************************************/
static int get_sysparm(const char *parm_nam_program)
{
    EXEC SQL BEGIN DECLARE SECTION;
    static const char* sql_nam_program;
    EXEC SQL END DECLARE SECTION;

    char FNC[] = "get_sysparm";

    sql_nam_program = parm_nam_program;

    EXEC SQL SELECT date_parm_1,
                    dte_parm_2
               INTO :sql_date_parm_1,
                    :sql_dte_parm_2
               FROM t_system_parms
              WHERE nam_program = :sql_nam_program;

    if (NOT_SQL_OK)
    {
        fprintf(stderr, "Error retrieving from t_system_parms, "
                "nam_program: [%s] \n", parm_nam_program);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }
    return FUNCTION_SUCCESSFUL;
}
/************************************************************************/
/*                                                                      */
/*  Function Name: validate_cur_assigns                                 */
/*                                                                      */
/*  Description:   This function verifies that the beneficiary is still */
/*                 eligible for programs in which they are currenlty    */
/*                 enrolled (have assignments).                         */
/*                                                                      */
/************************************************************************/
static int validate_cur_assigns(void)
{
    bool eligible = mgd_FALSE;
    bool pmpmatch = mgd_FALSE;

    int rc;
    struct mc_rtn_message rtn_msg_str;
    struct mc_eligible elig_info_str;
    struct mc_aid_elig aid_elig_str;
    struct mc_is_reason rtn_data_str_ptr;
    struct mc_noncap_eligible noncap_elig_str;
    struct mc_eligible mc_is_sed_loc;
    int temp_elig_dte;
    int temp_end_dte;
    int temp_prev_mth_enddte;
    char* elig_end_rsn;

    char FNC[] = "validate_cur_assigns";

    enrolled_nemt  = mgd_FALSE;

    sql_dte_end = sql_pntl_rcd.dte_effective;
    EXEC SQL OPEN pmp_assign_cursor;

    if (NOT_SQL_OK)
    {
        fprintf(stderr, "ERROR: Unable to open pmp_assign_cursor, "
                        "sak_recip: %d\n", sql_pntl_rcd.sak_recip);
        c_do_display_rtn_msg(&rtn_msg_str, FNC);
        ROLLBACK();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    while ( fetch_assign() == ANSI_SQL_OK )
    {
      if (arg_rsn_rpting == mgd_TRUE)
      {
         fprintf(err_rpt_file_ptr, "\t\tbeneficiary [%d] has assignment for pgm [%d] on/after [%d] ....",
                                    sql_pntl_rcd.sak_recip, pmp_assign_info.sak_pub_hlth, benefit_mth_start);
      }

        /* set the date to use for eligibility check */
        temp_elig_dte = sql_pntl_rcd.dte_effective;

        /* check that bene is still eligible for the current assignment */
        eligible = c_is_mc_eligible_3(&elig_info_str, &mc_rtn_msg_str,
                                      &aid_elig_str, &noncap_elig_str,
                                      sql_pntl_rcd.sak_recip,
                                      pmp_assign_info.sak_pub_hlth,
                                      temp_elig_dte, recip_dte_death,
                                      mgd_TRUE, mgd_FALSE,
                                      'D');

        if (eligible == FUNCTION_ERROR)
        {
            c_do_display_rtn_msg(&mc_rtn_msg_str, FNC);
            EXEC SQL CLOSE pmp_assign_cursor;
            ROLLBACK();
            ind_abend = mgd_TRUE;
            finalize();
            exit(FAILURE);
        }

        if (eligible)
        {
           if (noncap_elig_str.noncap_elig_code > 0)
           {
               if (sak_pmp_svc_nemt_nocap == pmp_assign_info.sak_pmp_ser_loc)
                 pmpmatch = mgd_TRUE;
               else
                 pmpmatch = mgd_FALSE;
           }
           else
           {
              if (sak_pmp_svc_nemt_cap == pmp_assign_info.sak_pmp_ser_loc)
                 pmpmatch = mgd_TRUE;
              else
                 pmpmatch = mgd_FALSE;
           }
        }

        /* bene is eligible to continue assignment */
        if ((eligible) && (pmpmatch))
        {
           if (arg_rsn_rpting == mgd_TRUE)
           {
            fprintf(err_rpt_file_ptr, "[ELIGIBLE! continue assignment]\n");
           }

           if (pmp_assign_info.sak_pub_hlth == sak_pub_hlth_nemt)
               enrolled_nemt = mgd_TRUE;
           else {
                  fprintf(stderr, "ERROR: Unable to determine sak_pub_hlth and set enrolled flags, "
                                  "sak_pub_hlth: %d\n", pmp_assign_info.sak_pub_hlth);
                  c_do_display_rtn_msg(&mc_rtn_msg_str, FNC);
                  EXEC SQL CLOSE pmp_assign_cursor;
                  ROLLBACK();
                  ind_abend = mgd_TRUE;
                  finalize();
                  exit(FAILURE);
                 }

           /* don't check future segs */
           if (pmp_assign_info.dte_effective <= last_kaecses_elig_dte)
           {   /* check for change in aid cat */
                if ( verify_aid_elig() != FUNCTION_SUCCESSFUL )
                {
                   fprintf(stderr, "ERROR: Unable to change aid elig for, "
                                  "sak_recip: [%d] sak_pgm_elig [%d] \n",
                                   sql_pntl_rcd.sak_recip, pmp_assign_info.sak_pgm_elig);
                   c_do_display_rtn_msg(&mc_rtn_msg_str, FNC);
                   EXEC SQL CLOSE pmp_assign_cursor;
                   ROLLBACK();
                   ind_abend = mgd_TRUE;
                   finalize();
                   exit(FAILURE);
                }

            }
        }

        /* Not eligible OR not a good pmp match end/hist the assignment for monthly cycle*/
        /* for daily cycle if the eligibility rcvd is new then end/hist assignment otherwise leave as is */
        /* if the bene has SED LOC and the eligibility is not new then SED wins so end assign and create new */
        if (!eligible || !pmpmatch)
        {
           if (arg_rsn_rpting == mgd_TRUE)
           {
            fprintf(err_rpt_file_ptr, "[NOT ELIG/PMP MISMTH]- end/hist assign\n");
           }

           if (!pmpmatch)
              elig_end_rsn = (char*)NEMT_ELIG_CHG_REASON;
           else
              elig_end_rsn = (char*)MC_ELIG_END_REASON;

           /* history assign, assign not started, eff date is in future */
           if (pmp_assign_info.dte_effective >= temp_elig_dte)
           {
               rc = c_do_hist_pmp_assign( &mc_rtn_msg_str, sql_pntl_rcd.sak_recip,
                                          pmp_assign_info.sak_re_pmp_assign,
                                          pmp_assign_info.sak_pgm_elig,
                                          pmp_assign_info.dte_effective,
                                          elig_end_rsn,
                                          SAK_ENT_NEMT_AUTOASSIGN);

               if (rc != FUNCTION_SUCCESSFUL)
               {
                  fprintf(stderr, "ERROR: Unable to history off the assignment, "
                                  "sak_recip: [%d] sak_pgm_elig [%d] \n",
                                  sql_pntl_rcd.sak_recip, pmp_assign_info.sak_pgm_elig);
                  c_do_display_rtn_msg(&mc_rtn_msg_str, FNC);
                  EXEC SQL CLOSE pmp_assign_cursor;
                  ROLLBACK();
                  ind_abend = mgd_TRUE;
                  finalize();
                  exit(FAILURE);

               }
           }
           else  /* otherwise, end date the assignment */
           {
               /* get benefit month's previous month enddate to end assignent */
               temp_prev_mth_enddte = c_get_month_end(c_get_month_begin_prev(temp_elig_dte));

               rc = c_do_end_pmp_assign_2(&mc_rtn_msg_str, sql_pntl_rcd.sak_recip,
                                          pmp_assign_info.sak_pgm_elig,
                                          temp_prev_mth_enddte,
                                          elig_end_rsn,
                                          SAK_ENT_NEMT_AUTOASSIGN);

               if (rc != FUNCTION_SUCCESSFUL)
               {
                  fprintf(stderr, "ERROR: Unable to end date the assignment, "
                                  "sak_recip: [%d] sak_pgm_elig [%d] \n",
                                   sql_pntl_rcd.sak_recip, pmp_assign_info.sak_pgm_elig);
                  c_do_display_rtn_msg(&mc_rtn_msg_str, FNC);
                  EXEC SQL CLOSE pmp_assign_cursor;
                  ROLLBACK();
                  ind_abend = mgd_TRUE;
                  finalize();
                  exit(FAILURE);
               }
           }
        }

    } /* end while */

    if (SQL_ERROR)
    {
        EXEC SQL CLOSE pmp_assign_cursor;
        c_do_display_rtn_msg(&rtn_msg_str, FNC);
        ROLLBACK();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    EXEC SQL CLOSE pmp_assign_cursor;

    if (SQL_ERROR)
    {
        fprintf(stderr, "ERROR: Could not close pmp_assign_cursor, "
                "sak_recip: %d\n", sql_pntl_rcd.sak_recip);
        c_do_display_rtn_msg(&rtn_msg_str, FNC);
        ROLLBACK();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }
    return FUNCTION_SUCCESSFUL;
}

/*********************************************************************/
/*                                                                   */
/* Function:     fetch_assign()                                      */
/*                                                                   */
/* Description:  Retrieve an assignment from the pmp_assign_cursor.  */
/*                                                                   */
/*********************************************************************/
static int fetch_assign(void)
{
    char FNC[] = "fetch_assign";

    pmp_assign_info.sak_recip = 0;
    pmp_assign_info.sak_re_pmp_assign = 0;
    pmp_assign_info.sak_pgm_elig = 0;
    pmp_assign_info.sak_pub_hlth = 0;
    pmp_assign_info.dte_effective = 0;
    pmp_assign_info.dte_end = 0;
    pmp_assign_info.sak_pmp_ser_loc = 0;
    pmp_assign_info.cde_rsn_mc_start[0] = '\0';

    EXEC SQL FETCH pmp_assign_cursor
    INTO
        :pmp_assign_info.sak_recip,
        :pmp_assign_info.sak_re_pmp_assign,
        :pmp_assign_info.sak_pgm_elig,
        :pmp_assign_info.sak_pub_hlth,
        :pmp_assign_info.dte_effective,
        :pmp_assign_info.dte_end,
        :pmp_assign_info.sak_pmp_ser_loc,
        :pmp_assign_info.cde_rsn_mc_start;

    if (SQL_ERROR)
    {
        fprintf(stderr, "ERROR: Unable to fetch from pmp_assign_cursor.\n");
        ROLLBACK();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    return(sqlca.sqlcode);
}

/************************************************************************/
/*                                                                      */
/*  Function Name: process_death                                        */
/*                                                                      */
/*  Description:   History off assignments that became effective after  */
/*                 the date of death.  End date any active assignments  */
/*                 with the date of death.                              */
/*                                                                      */
/************************************************************************/
static void process_death(void)
{
    int rc = FUNCTION_SUCCESSFUL;
    char FNC[] = "process_death";

    if (recip_dte_death != 0)
    {
        sql_dte_end = recip_dte_death;
        EXEC SQL OPEN pmp_assign_cursor;
        if (NOT_SQL_OK)
        {
          fprintf(stderr, "ERROR: Unable to open pmp_assign_cursor in process_death, "
                "sak_recip: %d\n", sql_pntl_rcd.sak_recip);
          c_do_display_rtn_msg(&mc_rtn_msg_str, FNC);
          ROLLBACK();
          ind_abend = mgd_TRUE;
          finalize();
          exit(FAILURE);
        }

        while (fetch_assign() == ANSI_SQL_OK)
        {
          /* History off assignments that became effective after date of death */
          if (pmp_assign_info.dte_effective > recip_dte_death)
          {
            rc = c_do_hist_pmp_assign(&mc_rtn_msg_str,
                                      sql_pntl_rcd.sak_recip,
                                      pmp_assign_info.sak_re_pmp_assign,
                                      pmp_assign_info.sak_pgm_elig,
                                      pmp_assign_info.dte_end,
                                      (char*)ELIG_DEATH_REASON,
                                      SAK_ENT_NEMT_AUTOASSIGN);

          }
          else if (pmp_assign_info.dte_end > recip_dte_death)
          {
            /* End date active assignments with the date of death */
            rc = c_do_end_pmp_assign_2(&mc_rtn_msg_str,
                                       sql_pntl_rcd.sak_recip,
                                       pmp_assign_info.sak_pgm_elig,
                                       recip_dte_death,
                                       (char*)ELIG_DEATH_REASON,
                                       SAK_ENT_NEMT_AUTOASSIGN);
          }

          if (rc != FUNCTION_SUCCESSFUL)
          {
             fprintf(stderr, "ERROR: Unable to end date/history off the assignment, "
                "sak_recip: %d\n", sql_pntl_rcd.sak_recip);
            sqlerror();
            ind_abend = mgd_TRUE;
            finalize();
            exit(FAILURE);
          }
        } /* end while */

        EXEC SQL CLOSE pmp_assign_cursor;

        if (NOT_SQL_OK)
        {
           fprintf(stderr, "ERROR: Unable to close pmp_assign_cursor in process_death, "
                "sak_recip: %d\n", sql_pntl_rcd.sak_recip);
           c_do_display_rtn_msg(&mc_rtn_msg_str, FNC);
           ROLLBACK();
           ind_abend = mgd_TRUE;
           finalize();
           exit(FAILURE);
        }
/*      return mgd_TRUE;/ * bene is NOT alive */
    }
/*  return mgd_FALSE; / * bene is alive */
}

/*********************************************************************/
/*                                                                   */
/*  Function Name: write_rpt_tbl                                     */
/*                                                                   */
/*  Description: Writes the beneficiary info to a table to be used   */
/*               used later by a report program.                     */
/*                                                                   */
/*********************************************************************/
static void write_rpt_tbl(int cde_rsn)
{
    char FNC[] = "write_rpt_tbl";

    if (arg_rsn_rpting == mgd_TRUE)
    {
        fprintf(err_rpt_file_ptr, "\t\t\tPMP MISMATCH for pgm [%d], writing to error rpt\n",sak_pub_hlth_nemt);
    }

    report_rec.sak_recip = sql_pntl_rcd.sak_recip;
    report_rec.sak_pub_hlth = assign_info_str.sak_pub_hlth;
    report_rec.dte_effective =  sql_pntl_rcd.dte_effective;
    report_rec.sak_pmp_ser_loc = assign_info_str.sak_pmp_ser_loc;
    report_rec.sak_re_pmp_assign = assign_info_str.sak_re_pmp_assign;
    report_rec.cde_rsn = cde_rsn;

    EXEC SQL INSERT INTO t_mc_re_error (sak_recip, sak_re_pmp_assign, sak_pub_hlth,
                                        dte_effective, sak_pmp_ser_loc, cde_rsn)
        VALUES (:report_rec.sak_recip,
                :report_rec.sak_re_pmp_assign,
                :report_rec.sak_pub_hlth,
                :report_rec.dte_effective,
                :report_rec.sak_pmp_ser_loc,
                :report_rec.cde_rsn);

    if (NOT_SQL_OK && sqlca.sqlcode != -1) /* already exists */
    {
        fprintf(stderr, "Error: unable to write to report error table.\n");
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    cnt_nemt_report++;

    return;
}
/************************************************************************/
/*                                                                      */
/*  Function Name: verify_aid_elig                                      */
/*                                                                      */
/*  Description:   This function will compare the sak_cde_aid of the    */
/*                 Medicaid aid category eligibility with that of the   */
/*                 Managed Care aid category eligibility.  If there is  */
/*                 difference, we end date the existing segment and     */
/*                 create a new segment with the proper aid category.   */
/*                                                                      */
/************************************************************************/
static int verify_aid_elig(void)
{
    int temp_end_dte;
    int mc_sak_cde_aid; /* managed care sak_cde_aid */
    char FNC[] = "verify_aid_elig";

    if (RETRO_ELIG_MONTH)
        return FUNCTION_SUCCESSFUL;

    get_sak_pgm_elig();
    mcaid_sak_cde_aid = get_sak_cde_aid(benefit_mth_start, benefit_mth_end);

    sql_sak_pgm_elig = pmp_assign_info.sak_pgm_elig;
    mc_sak_cde_aid = get_sak_cde_aid(benefit_mth_start, benefit_mth_end);

    /* if there has been a change in aid categories, and the assign
       effective date is <= to benefit mth */
    if ( (mc_sak_cde_aid != mcaid_sak_cde_aid) &&
         (pmp_assign_info.dte_effective <= benefit_mth_start) )
    {
        /* if assn eff date is for future or benefit mth */
        if (sql_aid_dte_effective >= benefit_mth_start)
        {
            if (history_aid_seg() != FUNCTION_SUCCESSFUL)
                   return FUNCTION_FAILED;
        }
        else
        {
            /* segment has already been active */
            if (end_date_aid_elig(benefit_mth_prev_end_dte) != FUNCTION_SUCCESSFUL)
                  return FUNCTION_FAILED;
        }
        /* create new seg for new aid cat */
        if (new_aid_elig_seg(benefit_mth_start) != FUNCTION_SUCCESSFUL)
            return FUNCTION_FAILED;
    }

    return FUNCTION_SUCCESSFUL;
}

/************************************************************************/
/*                                                                      */
/*  Function Name: get_sak_cde_aid                                      */
/*                                                                      */
/*  Description:   This function will get the sak_cde_aid on the        */
/*                 segment for comparison purposes.                     */
/*                                                                      */
/*  Returns: int - sql_sak_cde_aid                                     */
/*                                                                      */
/************************************************************************/
static int get_sak_cde_aid(int start_dte, int end_dte)
{
    EXEC SQL BEGIN DECLARE SECTION;
    int sql_start_dte;
    int sql_end_dte;
    EXEC SQL END DECLARE SECTION;

    char FNC[] = "get_sak_cde_aid";

    sql_start_dte = start_dte;
    sql_end_dte = end_dte;

    EXEC SQL SELECT sak_cde_aid,
                    sak_aid_elig,
                    dte_effective,
                    dte_end
               INTO
                   :sql_sak_cde_aid,
                   :sql_sak_aid_elig,
                   :sql_aid_dte_effective,
                   :sql_aid_dte_end
               FROM t_re_aid_elig
              WHERE sak_recip    = :sql_pntl_rcd.sak_recip
                AND sak_pgm_elig = :sql_sak_pgm_elig
                AND dte_effective <= :sql_end_dte       /*  benefit_mth_end */
                AND dte_end >= :sql_start_dte          /*  benefit_mth_start */
                AND cde_status1 <> 'H'
                AND rownum < 2;

    if (NOT_SQL_OK)
    {
        fprintf(stderr, "Error: Unable to get sak_cde_aid from t_re_aid_elig.\n"
                         "sak_recip     : %d\n"
                         "dte_effective : %d\n"
                         "sak_pgm_elig  : %d\n",
                         sql_pntl_rcd.sak_recip, sql_start_dte, sql_sak_pgm_elig);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }

    return sql_sak_cde_aid;
}

/************************************************************************/
/*                                                                      */
/*  Function Name: get_sak_pgm_elig                                     */
/*                                                                      */
/*  Description: Get the sak_pgm_elig field from t_re_elig for the      */
/*               the active segment during the benefit month.           */
/*                                                                      */
/************************************************************************/
static int get_sak_pgm_elig(void)
{
    char FNC[] = "get_sak_pgm_elig";

    EXEC SQL
    SELECT sak_pgm_elig
    INTO :sql_sak_pgm_elig
    FROM t_re_elig
    WHERE
        sak_recip    = :sql_pntl_rcd.sak_recip
    AND sak_pub_hlth = :sak_pub_hlth_mcaid
    AND dte_effective <= :benefit_mth_end
    AND dte_end >= :benefit_mth_start
    AND cde_status1 <> 'H';

    /* should always be found since eligibility was found */
    if (NOT_SQL_OK)
    {
        fprintf(stderr, "\n*** ERROR: Unable to get sak_pgm_elig.\n"
                "sak_recip: %d\nsak_pub_hlth: %d\n"
                "benefit_mth_start: %d\nbenefit_end_dte: %d",
                sql_pntl_rcd.sak_recip, sak_pub_hlth_mcaid, benefit_mth_start,
                benefit_mth_end);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);
    }
    return FUNCTION_SUCCESSFUL;
}

/************************************************************************/
/*                                                                      */
/*  Function Name: history_aid_seg                                      */
/*                                                                      */
/*  Description:   History off an aid elig segment.                     */
/*                                                                      */
/************************************************************************/
static int history_aid_seg(void)
{
    char FNC[] = "history_aid_seg";

    /* history off all segments in the assignment group */
    EXEC SQL UPDATE t_re_aid_elig
        SET
        dte_end = :sql_aid_dte_effective,
        dte_active_thru = :sql_aid_dte_effective,
        dte_last_updated = :dte_current,
        cde_status1 = 'H'
            WHERE cde_status1 <> 'H' AND
                  dte_effective = :sql_aid_dte_effective AND
                (sak_recip, sak_pgm_elig) IN ( (
                SELECT sak_recip, sak_pgm_elig
                FROM t_re_pmp_assign
                WHERE sak_re_pmp_assign IN (
                    SELECT sak_re_pmp_assign
                    FROM t_mc_assign_grp
                    WHERE sak_assign_grp = (
                        SELECT sak_assign_grp
                        FROM t_mc_assign_grp
                        WHERE sak_re_pmp_assign = (
                            SELECT sak_re_pmp_assign
                            FROM t_re_pmp_assign
                            WHERE sak_recip = :sql_pntl_rcd.sak_recip
                            AND sak_pgm_elig = :sql_sak_pgm_elig) ) ) )
            union
                SELECT :sql_pntl_rcd.sak_recip, :sql_sak_pgm_elig
                FROM DUAL);

    if (NOT_SQL_OK)
    {
        fprintf(stderr, "ERROR: Unable to history off eligibility segment.\n"
                "sak_recip    : %d\n"
                "sak_pgm_elig : %d\n"
                "sak_aid_elig : %d\n"
                "dte_end      : %d\n",
                sql_pntl_rcd.sak_recip, sql_sak_pgm_elig,
                sql_sak_aid_elig, sql_aid_dte_end);
        sqlerror();
        ind_abend = mgd_TRUE;
        finalize();
        exit(FAILURE);

    }

    return FUNCTION_SUCCESSFUL;
}

/************************************************************************/
/*                                                                      */
/*  Function Name: end_date_aid_elig                                    */
/*                                                                      */
/*  Description:   This function will end date t_re_aid_elig for the    */
/*                 existing Managed Care segment.                       */
/*                                                                      */
/************************************************************************/
static int end_date_aid_elig(int parm_dte_end )
{
    char FNC[] = "end_date_aid_elig";
    EXEC SQL BEGIN DECLARE SECTION;
    int sql_dte_end;
    EXEC SQL END DECLARE SECTION;

    sql_dte_end = parm_dte_end;

    /* end the aid elig segment and all pgms in the assignment group */
    EXEC SQL UPDATE t_re_aid_elig
        SET dte_end = :sql_dte_end,
        dte_last_updated = :dte_current
        WHERE
        dte_effective <= :benefit_mth_end AND
        dte_end >= :benefit_mth_start AND
        cde_status1 <> 'H' AND
        (sak_recip, sak_pgm_elig) IN ( (
            SELECT sak_recip, sak_pgm_elig
            FROM t_re_pmp_assign
            WHERE sak_re_pmp_assign IN (
                SELECT sak_re_pmp_assign
                FROM t_mc_assign_grp
                WHERE sak_assign_grp = (
                    SELECT sak_assign_grp
                    FROM t_mc_assign_grp
                    WHERE sak_re_pmp_assign = (
                        SELECT sak_re_pmp_assign
                        FROM t_re_pmp_assign
                        WHERE sak_recip = :sql_pntl_rcd.sak_recip
                        AND sak_pgm_elig = :sql_sak_pgm_elig) ) ) )
             union
             SELECT :sql_pntl_rcd.sak_recip, :sql_sak_pgm_elig /*in case no grp */
             FROM DUAL);

    if (NOT_SQL_OK)
    {
        fprintf(stderr, "ERROR: Unable to end date Managed Care segment.\n"
                "benefit_start: %d, sak_pgm_elig: %d, sak_recip: %d",
                benefit_mth_start, sql_sak_pgm_elig, sql_pntl_rcd.sak_recip);
                sqlerror();
        ind_abend = mgd_TRUE;
        sqlerror();
        finalize();
        exit(FAILURE);
    }

    return FUNCTION_SUCCESSFUL;
}

/************************************************************************/
/*                                                                      */
/*  Function Name: new_aid_elig_seg                                     */
/*                                                                      */
/*  Description:   This function will insert a new segment into         */
/*                 t_re_aid_elig.  This new segment will match the      */
/*                 aid category of the Medicaid eligibility segment.    */
/************************************************************************/
static int new_aid_elig_seg(int parm_dte_eff)
{
    char FNC[] = "new_aid_elig_seg";
    EXEC SQL BEGIN DECLARE SECTION;
    int sql_dte_eff;
    int sql_dte_end;
    int new_sak_aid_elig = 0;
    int sql_sak_case = -1; /* Initial value for sak_case in t_re_aid_elig for mc aid segments */
    EXEC SQL END DECLARE SECTION;

    sql_dte_eff = parm_dte_eff;
    sql_dte_end = EOT;

    if (svchp == NULL)
        ociConnect( "AIM_PSWD");

    new_sak_aid_elig = sakIncrement(svchp, "SAK_AID_ELIG",
                                            "T_RE_AID_ELIG", mgd_TRUE);
    EXEC SQL
        INSERT INTO t_re_aid_elig(sak_aid_elig, sak_recip, sak_pgm_elig,
                              sak_cde_aid, sak_case, dte_effective, dte_end,
                              cde_status1, kes_cde_med_type, kes_cde_med_sub,
                              kes_cde_inv_med_sb, kes_cde_med_elig,
                              kes_cde_cash_type, kes_cde_cash_sub,
                              kes_cde_fund_src, kes_cde_typ_place,
                              kes_cde_legal_stat, kes_ind_qmb,
                              kes_cde_partic, kes_cde_lime, kes_cde_lime_title,
                              kes_cde_pdi,
                              dte_created, dte_last_updated, dte_active_thru)
        VALUES ( :new_sak_aid_elig, :sql_pntl_rcd.sak_recip, :sql_sak_pgm_elig,
                 :mcaid_sak_cde_aid, :sql_sak_case, :sql_dte_eff, :sql_dte_end,
                 ' ', /* cde_status1 */ '  ', '  ',
                 '  ', '  ',
                 '  ', '  ',
                 '  ', '  ',
                 '  ', ' ',
                 '  ', '  ', '  ',
                 '  ',
                 :dte_current, :dte_current, :EOT );

        if (NOT_SQL_OK)
        {
            fprintf(stderr,
                    "ERROR: Unable to create new eligibility segment.\n");
            ind_abend = mgd_TRUE;
            sqlerror();
            finalize();
            exit(FAILURE);
        }

    return FUNCTION_SUCCESSFUL;
}
