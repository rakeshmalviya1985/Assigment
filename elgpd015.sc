#pragma ident "@(#)elgpd015.sc	1.8 06/19/17 EDS"
/*********************************************************************/
/*                                                                   */
/*  PROGRAM:    elgpd015                                             */
/*                                                                   */
/*  DESCRIPTION:  This program reads data from T_RE_KEES_PLAN_UPDATE */
/*                table and call main_mgd_care_kaecses function in   */
/*                libmgd_kaecses.so program to make managed care     */
/*                assignment for eligibility update made through KEES*/
/*                                                                   */
/*                                                                   */
/*********************************************************************/
/*                                                                   */
/*                       Modification Log                            */
/*                                                                   */
/* Date     CSR   Author        Description                          */
/* -------- -----------------   ------------------------------------ */
/* 09/03/13 15002 Nirmal T      Initial release.                     */
/* 09/18/13 15002 Nirmal T      Made changes to continue even if     */
/*                              function call main_mgd_care_kaecses  */
/*                              returns error like elgpd05d program. */
/* 10/24/13 15002 Nirmal T      Corrected issues Date of Death issue */
/*                              where it was not calling MGD function*/
/*                              to end assignment in some situations.*/
/* 02/25/14 15002 Nirmal T      Added function call mgd_care_lotc.   */
/*                              Made changes to send an indicator col*/
/*                              to main_mgd_care_kaecses to show new */
/*                              eligibility or extending eligibility.*/
/*                              Fixed issue with Proration set when  */
/*                              calling month was not the current one*/
/*                              Made changes to find calling month   */
/*                              starting nextday of eligbility change*/
/* 09/08/15 16497 Titus J       Add SSDO processing.                 */
/* 10/14/15 16497 Titus J       Exclude SSDO if DOD is 19000101.     */
/* 06/19/17 17627 Doug Hawkins  Bug fix where record count is a      */
/*                              multiple of COMMIT_CNT. Using 17627  */
/*                              to expedite fix.                     */
/*********************************************************************/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "mgd_common.h"
#include "mgd_kaecses.h"

exec sql include sqlca;

#define COMMIT_CNT (1000)
#define COMMIT_IND (mgd_TRUE)          // mgd_FALSE - Rollback;  mgd_TRUE - Commit;

/* --- Files --- */

FILE *keesin,*rstfile;


/* --- Global variables --- */

static char time_stamp[64] = {'\0'};
static char *fname;
static char s_eff_dt[14+1] = {'\0'};
static char s_end_dt[14+1] = {'\0'};
static char s_eff_pr[14+1] = {'\0'};
static char s_end_pr[14+1] = {'\0'};
static char s_app_dt[14+1] = {'\0'};
static char s_mco_ch[2+1] = {'\0'};
static char c_run_pm      = {'\0'};
static char c_source      = {'\0'};
static char c_el_ind      = {'\0'};
static int  i_eff_dd = 0;                      // dd of effective for proration date.
static int  i_eff_cr = 0;                      // yyyymmdd of current effective date.
static int  i_end_cr = 0;                      // yyyymmdd of current end date.
static int  i_eff_pr = 0;                      // yyyymmdd of prior effective date.
static int  i_end_pr = 0;                      // yyyymmdd of prior end date.
static int  i_eff_wk = 0;                      // yyyymm of work effective date to run loop.
static int  i_end_wk = 0;                      // yyyymm of work end date to run loop.
static int  i_cmt_cnt = COMMIT_CNT;
static int  i_cmt_ind = COMMIT_IND;
static int  i_rec_cnt = 0;


/* --- SQL variables --- */

exec sql begin declare section;
    int sak_recip = 0;
    int  i_cyc_mm = 0;
    int  i_sak_pl[COMMIT_CNT] = { 0 };
exec sql end declare section;


/* --- Structure variables --- */

ELIG_REC  eligrec;

#pragma pack(1)
static struct {
    int  i_sak;
    char t_rcvd[14];
    char t_prcd[14];
    int  i_recp;
    char t_effc[14];
    char t_endc[14];
    char t_effp[14];
    char t_endp[14];
    char s_mcoc[2];
    char t_appl[14];
    char c_runp[1];
    char c_type[1];
} elg_st;
#pragma pack()


/* --- Functions --- */

static int  connectdb  (void);
static int  commitDb   (void);
static int  initialize (void);
static int  finalize   (void);
static int  mainline   (void);
static int  copyToVariable (void);
static int  loadEligRec   (void);
static int  deletePlanUpdate (void);
static int  getNextMonth  (int);
static int  getMonthendParm  (void);
static int  readRestartFile  (void);
static int  writeRestartFile (void);
static void mark_time  (char *);


/* ------------------ main () ------------------ */
int main (int argc, char *argv[])
{
    initialize ();
    mainline  ();
    finalize  ();

    return 0;
}


/* ------------------ mainline  () ------------------ */
int mainline ()
{
    int i_cnt = 0;

    readRestartFile ();
    if (i_rec_cnt > 0)
    {
        fprintf(stdout, "...Skipping to record # %d\n", i_rec_cnt);
        while (i_cnt < i_rec_cnt)
        {
             fread(&elg_st, (int) sizeof(elg_st), 1, keesin);
             i_cnt++;
        }
    }

    i_cnt = 0;

    while (fread(&elg_st, (int) sizeof(elg_st), 1, keesin) == 1)
    {
        i_rec_cnt++;
        copyToVariable ();

        // SSDO - DOD situation.
        if (c_source == 'D' && i_end_cr > 19000101)
        {
            if (mgd_care_ssdo(sak_recip, i_end_cr) == 0)
            {
                fprintf(stdout, "ERROR: Call to MC SSDO failed. Record [%8d]\n", i_rec_cnt);
                fprintf(stdout, "DEBUG INFO: sak_recip[%d], DOD[%d]\n\n", sak_recip, i_end_cr);
            }
        }

        // LOC changes received.
        else if (c_source == 'L')
        {
            if (mgd_care_lotc(sak_recip, ((i_eff_cr > i_eff_pr) ? i_eff_cr : i_eff_pr)) == 0)
            {
                fprintf(stdout, "ERROR: Call to MC LOTC failed. Record [%8d]\n", i_rec_cnt);
                fprintf(stdout, "DEBUG INFO: sak_recip[%d], eff[%d]\n\n", sak_recip, ((i_eff_cr > i_eff_pr) ? i_eff_cr : i_eff_pr));
            }
        }

        // ELIG changes received.
        else
        {
            // Newly eligibles.
            if ( (i_eff_pr == 19000101) && (i_end_pr == 19000101))
            {
                i_eff_wk = (i_eff_cr/100);
                i_end_wk = (i_end_cr/100);
                i_eff_dd = (i_eff_cr%100);
                c_el_ind = 'A';
            }
    
            // Lost eligiblity.
            else if ( (i_eff_cr == 19000101) && (i_end_cr == 19000101))
            {
                i_eff_wk = (i_eff_pr/100);
                i_end_wk = (i_end_pr/100);
                c_el_ind = 'R';
            }
    
            // all other eligibility changes, the effective date will not change.
            else
            {
                if (i_end_cr > i_end_pr)
                {
                    i_eff_wk = (c_do_add_day(i_end_pr)/100);     // start after previous end date.
                    i_end_wk = (i_end_cr/100);
                }
                else
                {
                    i_eff_wk = (i_end_cr/100);
                    i_end_wk = (i_end_pr/100);
                }
                c_el_ind = 'U';
            }
     
    
            while (i_eff_wk <= i_end_wk) 
            {
                int i_sak_aid_elig = 0;  /*so mgd_care routine will get it's own sak_aid_elig*/

                loadEligRec ();

                if (main_mgd_care_kaecses(&eligrec, sak_recip, c_run_pm, &i_sak_aid_elig) == 0)
                {
                    fprintf(stdout, "ERROR: Call to MC assignment failed. Record [%8d]\n", i_rec_cnt);
                    fprintf(stdout, "DEBUG INFO: sak_recip [%d], Benefit month [%d]\n\n", sak_recip, i_eff_wk);
                }
    
                i_eff_wk = getNextMonth (i_eff_wk);
    
                if (i_eff_wk > i_cyc_mm)
                    break;
            }
        }

        i_sak_pl[i_cnt] = elg_st.i_sak;
        i_cnt++;

        if ((i_rec_cnt % i_cmt_cnt) == 0)
        {
            commitDb ();
            writeRestartFile ();
            i_cnt = 0;
        }

    }

    if (i_cnt)
        commitDb ();

    return 0;
}


/* ------------------ copyToVariable () ------------------ */
int copyToVariable ()
{
    int t = 0;

    sak_recip = elg_st.i_recp;
    memcpy(s_eff_dt, elg_st.t_effc, (int) sizeof(s_eff_dt) -1);
    memcpy(s_end_dt, elg_st.t_endc, (int) sizeof(s_end_dt) -1);
    memcpy(s_eff_pr, elg_st.t_effp, (int) sizeof(s_eff_pr) -1);
    memcpy(s_end_pr, elg_st.t_endp, (int) sizeof(s_end_pr) -1);
    memcpy(s_app_dt, elg_st.t_appl, (int) sizeof(s_app_dt) -1);
    memcpy(s_mco_ch, elg_st.s_mcoc, (int) sizeof(s_mco_ch) -1);
    c_run_pm = elg_st.c_runp[0];
    c_source = elg_st.c_type[0];

    sscanf(s_eff_dt, "%8d%6d", &i_eff_cr, &t);
    sscanf(s_end_dt, "%8d%6d", &i_end_cr, &t);
    sscanf(s_eff_pr, "%8d%6d", &i_eff_pr, &t);
    sscanf(s_end_pr, "%8d%6d", &i_end_pr, &t);

    return 0;
}


/* ------------------ loadEligRec () ------------------ */
int loadEligRec ()
{
    char s_pro_dt[8+1] = {'\0'};

    memset(&eligrec, '\0', (int) sizeof(ELIG_REC));

    eligrec.retro_t21[0] = ' ';
    eligrec.elig_bypass_ind[0] = c_el_ind;
    sprintf(eligrec.benefit_month, "%6d", i_eff_wk);
    memcpy(eligrec.prap_alert_1, s_mco_ch, (int) sizeof(eligrec.prap_alert_1));
    memcpy(eligrec.prap_alert_2, "  ", (int) sizeof(eligrec.prap_alert_2));
    memcpy(eligrec.prap_alert_3, "  ", (int) sizeof(eligrec.prap_alert_3));
    memcpy(eligrec.prap_alert_4, "  ", (int) sizeof(eligrec.prap_alert_4));
    memcpy(eligrec.prap_alert_5, "  ", (int) sizeof(eligrec.prap_alert_5));
    memcpy(eligrec.prap_alert_6, "  ", (int) sizeof(eligrec.prap_alert_6));
    memcpy(eligrec.prap_alert_7, "  ", (int) sizeof(eligrec.prap_alert_7));
    memcpy(eligrec.prap_alert_8, "  ", (int) sizeof(eligrec.prap_alert_8));
    memcpy(eligrec.prap_alert_9, "  ", (int) sizeof(eligrec.prap_alert_9));
    memcpy(eligrec.prap_alert_10, "  ", (int) sizeof(eligrec.prap_alert_10));
    memcpy(eligrec.client_date_of_application, s_app_dt, (int) sizeof(eligrec.client_date_of_application));

    if ((i_eff_dd > 1) && (i_eff_dd < 32))
    {
        sprintf(s_pro_dt, "%6d%02d", i_eff_wk, i_eff_dd);
        memcpy(eligrec.benefit_proration_ymd, s_pro_dt, (int) sizeof(eligrec.benefit_proration_ymd));
        i_eff_dd = 1;
    }
    else
        memcpy(eligrec.benefit_proration_ymd, "        ", (int) sizeof(eligrec.benefit_proration_ymd));


    return 0;
}


/* ------------------ deletePlanUpdate () ------------------ */
int deletePlanUpdate ()
{
    EXEC SQL
    DELETE FROM t_re_kees_plan_update
    WHERE  sak_kees_plan_update = :i_sak_pl
    ;

    if (sqlca.sqlcode != 0)
    {
        fprintf(stderr, "ERROR: Unable to delete data from t_re_kees_plan_update.\n");
        fprintf(stderr, "SQL ERROR: %s\n", sqlca.sqlerrm.sqlerrmc);
        exit (-1);
    }

    memset(i_sak_pl, 0, (int) sizeof(i_sak_pl));

    return 0;
}


/* ------------------ getNextMonth () ------------------ */
int getNextMonth (int a_mm)
{
    if ((a_mm % 100) == 12)
       a_mm = a_mm+89;
    else
       a_mm++;

    return a_mm;
}

/* ------------------ readRestartFile () ------------------ */
int readRestartFile ()
{
    if ((rstfile = fopen (fname, "r")) == NULL)
    {
        writeRestartFile ();
        i_rec_cnt = 0;
        return 0;
    }

    if (fscanf(rstfile, "%d", &i_rec_cnt) <= 0)
    {
        fprintf(stderr, "Unable to read restart file [%s][%d]\n", fname, i_rec_cnt);
        exit (-1);
    }

    fprintf(stdout, "\nFound restart file. Restarting after record # %d.\n\n", i_rec_cnt);

    fclose(rstfile);

    return 0;
}


/* ------------------ writeRestartFile () ------------------ */
int writeRestartFile ()
{
    if ((rstfile = fopen (fname, "w")) == NULL)
    {
        fprintf(stderr, "Restart file open error for write.[%s]\n", fname);
        exit (-1);
    }

    if (fprintf(rstfile, "%d", i_rec_cnt) <= 0)
    {
        fprintf(stderr, "Unable to write restart file [%s][%d]\n", fname, i_rec_cnt);
        exit (-1);
    }

    fclose(rstfile);
    rstfile = NULL;

    return 0;
}


/* ------------------ initialize () ------------------ */
int initialize ()
{

    fname = getenv ("dd_KEESINPT");
    if ((keesin = fopen (fname, "r")) == NULL)
    {
        fprintf(stderr, "Input file open error [%s]\n", fname);
        exit (-1);
    }

    if (i_cmt_ind)
    {
        fprintf(stdout, "\nCommit counter set to %d.\n\n", i_cmt_cnt);
    }
    else
    {
        fprintf(stdout, "------------------------------------------------------------------\n");
        fprintf(stdout, "--------------- Running in ROLLBACK mode. ------------------------\n");
        fprintf(stdout, "------------------------------------------------------------------\n");
    }

    fname = getenv ("dd_RST_FILE");

    if (connectdb ())
    {
        fprintf(stderr, "SQL ERROR: %s\n", sqlca.sqlerrm.sqlerrmc);
        exit (-1);
    }

    getMonthendParm ();

    return 0;
}


/* ------------------ getMonthendParm  () ------------------ */
int getMonthendParm ()
{

    EXEC SQL
    SELECT to_number(to_char(to_date(to_char(dte_parm_2), 'YYYYMMDD'), 'YYYYMM'))
    INTO   :i_cyc_mm
    FROM   t_system_parms
    WHERE  nam_program = 'KCSESLRM'
    ;

    if (sqlca.sqlcode != 0)
    {
        fprintf(stderr, "ERROR: Unable to retrieve KCSESLRM system parm.\n");
        fprintf(stderr, "SQL ERROR: %s\n", sqlca.sqlerrm.sqlerrmc);
        exit (-1);
    }

    return 0;
}


/* ------------------ commitDb  () ------------------ */
int commitDb ()
{
    mark_time(time_stamp);
    fprintf(stderr,"Processing record %6d at %s\n\n", i_rec_cnt, time_stamp);

    if (i_cmt_ind)
    {
        deletePlanUpdate ();
        exec sql commit;
    }
    else
    {
        exec sql rollback;
    }

    return 0;
}


/* ------------------ finalize  () ------------------ */
int finalize ()
{
    fclose (keesin);
    keesin = NULL;

    if (fname != NULL)
        remove (fname);

    fprintf(stdout,"Total no of records KAECSES read  [%8d]\n",i_rec_cnt);
    return 0;
}


/* ------------------ connectdb () ------------------ */
int connectdb ()
{
    exec sql begin declare section;
        char *connectstr;
    exec sql end declare section;

    connectstr = (char *) getenv("AIM_PSWD");

    if (connectstr)
    {
        exec sql connect :connectstr;
    }
    else
    {
        fprintf(stderr, "Environment variable AIM_PSWD undefined\n");
        return(-1);
    }

    return(sqlca.sqlcode);
}


/* ----------------- mark_time () ----------------- */
void mark_time( char *time_stamp)
{

    time_t curr_time;
    int len = sizeof("HH:MM:SS") + 1;

    curr_time = time(NULL);

    strftime( time_stamp, len, "%H:%M:%S", localtime(&curr_time) );
}

