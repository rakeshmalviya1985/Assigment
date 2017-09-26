#pragma ident "@(#)mgd0455d.sc	1.3 07/29/14 EDS"
/************************************************************************************/
/*                                                                                  */
/* PROGRAM: mgd0455d                                                                */
/*                                                                                  */
/* DESCRIPTION : Builds the daily KAECSES report which includes benes who have      */
/*               a HCBS or MFP level of care and no KanCare assignment.             */
/*               During the KAECSES cycle, if a record was processed and found      */
/*               to have the HCBS or MFP loc, the sak_recip is placed on            */
/*               the t_mc_re_error to be re-evaluated after auto assignment         */
/*               completes but before the MGD-0450-D report runs which truncates    */
/*               the t_mc_re_error table.                                           */
/*               7/29/2014 - Added SHSM & TCSM (170 & 171) to LOC list.             */
/*                                                                                  */
/*                                                                                  */
/* REPORT TITLE: MGD-0455-D                                                         */
/*              "Beneficiaries with LOC and no KanCare Assignment"                  */
/*                                                                                  */
/* INPUT PARMS: None                                                                */
/*                                                                                  */
/* INPUT FILES: None                                                                */
/*                                                                                  */
/* OUTPUT FILE: mgd45501.rpt.xxxx                                                   */
/*                                                                                  */
/* EXITS:       0 - success                                                         */
/*             -1 - failure                                                         */
/*                                                                                  */
/************************************************************************************/
/*                                                                                  */
/*                     Modification Log                                             */
/*                                                                                  */
/* Date        CSR/CO   Author            Description                               */
/* ----------  ------   --------------    -----------------                         */
/* 08/27/2013   15079   Doug Hawkins      Initial implementation.                   */
/* 02/26/2014   15079   Doug Hawkins      Add LOC list to getLOC, uncommented       */
/*                                        call to purgeTable.                       */
/* 07/29/2014   15507   Doug Hawkins      Changed title and added 170, 171 to LOCs. */
/************************************************************************************/

/*********************************************************************/
/*                             INCLUDES                              */
/*********************************************************************/
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>

#include "hmspconn.h"
#include "mrpf.h"
#include "mgd_common.h"


/*********************************************************************/
/*              TYPE DECLARATIONS AND INTERNAL STRUCTURES            */
/*********************************************************************/


/*********************************************************************/
/*                        FUNCTION PROTOTYPES                        */
/*********************************************************************/
int main(void);
static int openReportFile(void);
static int initReport (void);
static void gatherData(void);
static void cleanUp(void);
static void purgeTable(void);
static void showStats(void);
static char *formatDate(char *fmt_date, int date);
static int getReportDate(void);
static int fetch_err_data_cursor(void);
static int hasAssignment(void);
static int hasTMAssignment(void);
static void getBeneInfo(void);
static int getLOC(void);

/*********************************************************************/
/*                             MACROS                                */
/*********************************************************************/


/*********************************************************************/
/*                             CONSTANTS                             */
/*********************************************************************/
static const char rptTitle[] = "BENEFICIARIES WITH LOC AND NO KANCARE ASSIGNMENT";
static const char PROGNAME[] = "MGDJD455";    /* Process */
static const char rptId[]    = "MGD-0455-D";  /* Report */
static const char locId[]    = "MGD0455D";    /* Location */


/*********************************************************************/
/*                             GLOBALS                               */
/*********************************************************************/
static int g_rptFd;
static int rptTotal = 0;
static int locFailCnt = 0;
static int recTotal = 0;
static char loc_dte_formatted[10+1]; /* yyyy/mm/dd */


/*********************************************************************/
/*                             DB VARIABLES                          */
/*********************************************************************/
EXEC SQL INCLUDE sqlca.h;
EXEC SQL INCLUDE SQLCA;

EXEC SQL BEGIN DECLARE SECTION;
static char sql_beneName[33+1];     /* last(17), first(12) mi(1) */
static char sql_idMedicaid[12+1];
static char sql_cde_loc[3+1];
static char sql_cde_liv_arng[2+1];
static char sql_kes_cde_loc[2+1];
static int  sql_cde_rsn = 9999;     //Value used by mgd_kaecses_kc.sc
static int  sql_sak_recip;
static int  sql_sak_pub_hlth;
static int  sql_dte_effective;
static int  sql_loc_dte_effective;
EXEC SQL END DECLARE SECTION;


/*********************************************************************/
/* CURSOR NAME: err_data_cursor                                      */
/* DESCRIPTION: Read rows from the t_mc_re_error table.              */
/*********************************************************************/
EXEC SQL DECLARE err_data_cursor CURSOR FOR
SELECT sak_recip,
       sak_pub_hlth,
       dte_effective
  FROM t_mc_re_error
 WHERE cde_rsn = :sql_cde_rsn
 ORDER BY sak_recip
;


/*********************************************************************/
/*                             REPORT HEADER                         */
/*********************************************************************/
static HEADER *rpt;
static char *hdr[] =
{
/*
          1         2         3         4         5         6         7         8         9         10        11        12        13
 123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012
*/
"@H1                                                                                                                                 ",
"@H2                                                                                                                                 ",
"@H3                                                                                                                                 ",
"                                                 REPORT PERIOD: @<<<<<<<<<                                                          ",
"Beneficiary ID  Beneficiary Name                   Effective Date     LOC      Living Arr.   KAECSES LOC Code                       "
};

char *BLANK_LINE =
"                                                                                                                                    ";

static char  *dtl[] =
{
"@<<<<<<<<<<     @<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<  @<<<<<<<<<         @<<      @<            @<                                     "
};


static char  *footer[] =
{
"                                                 *** NO DATA THIS RUN ***                                                           ",
"Total Number Without Assignments: @##,###                                                                                           ",
"                                                   *** END OF REPORT ***                                                            "
};


/*********************************************************************/
/*                                                                   */
/*  Function Name:  main                                             */
/*                                                                   */
/*  Description:    Program entry point.                             */
/*                                                                   */
/*********************************************************************/
int main(void)
{
    char FNAME[] = "main()";

    /*****  Get a report file name and open it   *****/
    g_rptFd = openReportFile();

    if (g_rptFd == -1)
    {
        return -1;
    }

    /* connect to database */
    if ( ConnectDB() != SUCCESS )
    {
        fprintf(stderr, "%s: ERROR: unable to connect to database.\n",
                FNAME);
        return -1;
    }

    /*****  Initialize the reporting routines  *****/
    if (initReport() != 0)
    {
        return -1;
    }

    /* main function to gather the data and generate report */
    gatherData();

    cleanUp();

    purgeTable();

    showStats();

    return 0; /* success */
}

/*********************************************************************/
/*                                                                   */
/* Function Name: openReportFile                                     */
/*                                                                   */
/* Description: Open the file to which the report data will be       */
/*              written.                                             */
/*                                                                   */
/*********************************************************************/
static int openReportFile(void)
{
    char* rptFile;

    rptFile =
        getenv("dd_REPORT01") ? getenv("dd_REPORT01") : "dd_REPORT01";

    if ((g_rptFd = open (rptFile, O_WRONLY|O_CREAT, 0666)) < 0)
    {
        fprintf (stderr, "ERROR: could not open report file.\n");
        perror (rptFile);
        return -1;
    }

    return g_rptFd;
}

/*********************************************************************/
/*                                                                   */
/*  Function Name:  initReport                                       */
/*                                                                   */
/*  Description: Call the appropriate routines to create the report  */
/*               and add the headers to the report structure.        */
/*                                                                   */
/*********************************************************************/
static int initReport (void)
{
    char FNAME[] = "initReport()";
    static char period[10+1]; /* Report Period: mm/dd/ccyy */
    int rc;

    if ((rpt = DefineReport2 (g_rptFd, 55, (char*)rptId, "\0",
                              (char*)PROGNAME, (char*)locId,
                              (char*)rptTitle)) == NULL)
    {
        fprintf (stderr, "ERROR: could not define report.\n");
        return -1;
    }

    formatDate(period, getReportDate());

    /* Add all of the header lines */
    rc = AddHeader (rpt, hdr[0]);
    rc+= AddHeader (rpt, hdr[1]);
    rc+= AddHeader (rpt, hdr[2]);
    rc+= AddHeader (rpt, hdr[3], period);
    rc+= AddHeader (rpt, BLANK_LINE);
    rc+= AddHeader (rpt, hdr[4]);
    rc+= AddHeader (rpt, BLANK_LINE);
    if (rc != 0)
    {
        fprintf (stderr, "%s: ERROR: could not add headers\n", FNAME);
        return -1;
    }

    /* Success */
    return 0;
}

/*********************************************************************/
/*                                                                   */
/*  Function Name:  gatherData                                       */
/*                                                                   */
/*  Description: Queries the database, formats the data for the      */
/*               report, and writes records to the specified report  */
/*               file.                                               */
/*                                                                   */
/*  Parameters: int - file descriptor of open report file            */
/*                                                                   */
/*********************************************************************/
static void gatherData(void)
{

    EXEC SQL OPEN err_data_cursor;
    if (SQL_ERROR)
    {
        fprintf(stderr, "%s : Error, failed to open cursor."
                " OraError: %d\n", "gatherData", sqlca.sqlcode);
        cleanUp();
        PrintSQLError();
        exit(EXIT_FAILURE);
    }

    while ( fetch_err_data_cursor() )
    {
        if (hasAssignment() == mgd_FALSE ||
		    hasTMAssignment() == mgd_TRUE)
        {
            getBeneInfo();
            remove_trailing_whitespaces(sql_beneName);

            if (getLOC() == FUNCTION_SUCCESSFUL)
            {

                loc_dte_formatted[0] = '\0';
                formatDate(loc_dte_formatted, sql_loc_dte_effective);

                PrintDetail(rpt, dtl[0],
                            sql_idMedicaid,
                            sql_beneName,
                            loc_dte_formatted,
                            sql_cde_loc,
                            sql_cde_liv_arng,
                            sql_kes_cde_loc
                            );
                rptTotal++;
            }
            else
            {
                locFailCnt++;
            }
        }
        recTotal++;
    }

    EXEC SQL CLOSE err_data_cursor;
    if (SQL_ERROR)
    {
        fprintf(stderr, "%s : Error, failed to close cursor."
                " OraError: %d\n", "gatherData", sqlca.sqlcode);
        PrintSQLError();
        cleanUp();
        exit(EXIT_FAILURE);
    }

    if (rptTotal > 0)
    {
        PrintDetail (rpt, BLANK_LINE);
        PrintDetail (rpt, BLANK_LINE);
        PrintDetail (rpt, footer[1] , rptTotal);
        PrintDetail (rpt, BLANK_LINE);
        PrintDetail (rpt, BLANK_LINE);
        PrintDetail (rpt, footer[2]);
    }
    else
    {
        PrintDetail (rpt, BLANK_LINE);
        PrintDetail (rpt, BLANK_LINE);
        PrintDetail (rpt, footer[0]); /* no data to report */
    }
    return;
}

/*********************************************************************/
/*                                                                   */
/* Function Name: cleanUp                                            */
/*                                                                   */
/* Description: Close the input and output files.                    */
/*                                                                   */
/*********************************************************************/
static void cleanUp(void)
{
    char FNAME[] = "cleanUp";

    /*****   Close the report file *****/
    if (close(g_rptFd) != 0)
    {
        fprintf (stderr,
                 "%s: ERROR: could not close the report output file.\n",
                 FNAME);
        exit(EXIT_FAILURE);
    }
    return;
}

/*********************************************************************/
/*                                                                   */
/* Function Name: purgeTable                                         */
/*                                                                   */
/* Description: Delete the 9999 records from table.                  */
/*                                                                   */
/*********************************************************************/
static void purgeTable(void)
{
    char FNAME[] = "purgeTable";
    /*****   remove records from the input table *****/
    EXEC SQL DELETE t_mc_re_error
        WHERE cde_rsn = :sql_cde_rsn;

    if (SQL_ERROR)
    {
        fprintf(stderr, "ERROR deleting records t_mc_re_error table, "
                        "rollback updates.\n\n");
        PrintSQLError();
        EXEC SQL rollback;
        exit(EXIT_FAILURE);
    }
    else
    {
        EXEC SQL commit;
        if (SQL_ERROR)
        {
            fprintf(stderr, "ERROR committing changes to database.\n\n");
            PrintSQLError();
            exit(EXIT_FAILURE);
        }
    }

    return;
}

/*********************************************************************/
/*                                                                   */
/* Function Name: showStats                                          */
/*                                                                   */
/* Description: Show useful information.                             */
/*                                                                   */
/*********************************************************************/
static void showStats(void)
{
    fprintf(stderr, "************************************************\n");
    fprintf(stderr, "************************************************\n");
    fprintf(stderr, "  Total Number of records Processed:       %d\n", recTotal);
    fprintf(stderr, "  Number of Beneficiaries on report:       %d\n", rptTotal);
    fprintf(stderr, "  Number of LOCs not qualifying to report: %d\n", locFailCnt);
    fprintf(stderr, "************************************************\n");
}

/*********************************************************************/
/*                                                                   */
/* Function:     formatDate()                                        */
/*                                                                   */
/* Description:  Takes the input parm (int) in the form YYYYMMDD     */
/*               and reformats it to MM/DD/CCYY.                     */
/*                                                                   */
/* Parameters:   pointer to formatted date string (must be at least  */
/*               10+1 chars) and a date in int fmt yyyymmdd          */
/*                                                                   */
/*********************************************************************/
static char *formatDate(char *fmt_date, int date)
{
    int yyyy;
    int mm;
    int dd;

    if (date > 0)
    {
        yyyy = date / 10000;
        mm   = (date % 10000) / 100;
        dd   = date % 100;
        sprintf(fmt_date, "%02d/%02d/%4d", mm, dd, yyyy);
    }
    return(fmt_date);
}

/************************************************************************/
/*                                                                      */
/*  Function Name:   getReportDate                                      */
/*                                                                      */
/*  Description:     Retrieve date system date                          */
/*  Returns:         Current date (YYYYMMDD)                            */
/*                                                                      */
/************************************************************************/
static int getReportDate(void)
{
    char FNAME[] = "getReportDate()";

    EXEC SQL BEGIN DECLARE SECTION;
    int sql_dte = 0;    /* current date, YYYYMMDD */
    EXEC SQL END DECLARE SECTION;

    EXEC SQL
    SELECT TO_NUMBER(TO_CHAR(SYSDATE, 'YYYYMMDD'))
    INTO :sql_dte
    FROM dual;

    if (sqlca.sqlcode != 0)
    {
        fprintf (stderr,
                 "%s, ERROR: could not retrieve system date\n", FNAME);
        fprintf(stderr, "SQL Error Message: [%s]\n",sqlca.sqlerrm.sqlerrmc);
        exit(EXIT_FAILURE);
    }
    return sql_dte;
}

/*********************************************************************/
/*                                                                   */
/* Function Name: fetch_err_data_cursor                              */
/*                                                                   */
/* Description: Read a record from the input table.                  */
/*                                                                   */
/*********************************************************************/
static int fetch_err_data_cursor(void)
{
    char FNAME[] = "fetch_err_data_cursor";

    EXEC SQL FETCH err_data_cursor
    INTO :sql_sak_recip,
         :sql_sak_pub_hlth,
         :sql_dte_effective;

    if (SQL_OK)
    {
        return mgd_TRUE;
    }
    else if (SQL_ERROR)
    {
        fprintf (stderr,
                 "%s, ERROR: could not retrieve input data.\n", FNAME);
        fprintf(stderr, "SQL Error Message: [%s]\n",sqlca.sqlerrm.sqlerrmc);
        cleanUp();
        exit(EXIT_FAILURE);
    }

    return mgd_FALSE;  /* no more data in cursor */
}

/*********************************************************************/
/*                                                                   */
/* Function Name: hasAssignment                                      */
/*                                                                   */
/* Description: Checks to see if bene has a KanCare assignment.      */
/* Returns:  mgd_TRUE - has a KanCare assignment                     */
/*           mgd_FALSE - no KanCare assignment                       */
/*                                                                   */
/*********************************************************************/
static int hasAssignment(void)
{
    char FNAME[] = "hasAssignment";

    EXEC SQL BEGIN DECLARE SECTION;
        int sql_count = 0;
    EXEC SQL END DECLARE SECTION;

    EXEC SQL
        SELECT COUNT(*)
          INTO  :sql_count
          FROM  t_re_elig elig,
                t_re_pmp_assign assign
         WHERE  elig.sak_recip = :sql_sak_recip
           AND  :sql_dte_effective BETWEEN elig.dte_effective AND elig.dte_end
           AND  elig.cde_status1 <> 'H'
           AND  elig.sak_pub_hlth IN (80,81)
           AND  assign.sak_recip = elig.sak_recip
           AND  assign.sak_pgm_elig = elig.sak_pgm_elig;
    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: Could not get assignment count for sak_recip [%d]!\n", FNAME, sql_sak_recip);
        PrintSQLError();
        cleanUp();
        exit(FAILURE);
    }
    if ( sql_count == 0)
    {
        return mgd_FALSE;   //no assignment
    }
    return mgd_TRUE;    //has assignment
}

/*********************************************************************/
/*                                                                   */
/* Function Name: hasTMAssignment                                    */
/*                                                                   */
/* Description: Checks to see if bene has a TXIX or MN eligibility . */
/* Returns:  mgd_TRUE - has TXIX or MN eligibility                   */
/*           mgd_FALSE - no TXIX or MN eligibility                   */
/*                                                                   */
/*********************************************************************/
static int hasTMAssignment(void)
{
    char FNAME[] = "hasTMAssignment";

    EXEC SQL BEGIN DECLARE SECTION;
        int sql_count = 0;
    EXEC SQL END DECLARE SECTION;

    EXEC SQL
        SELECT COUNT(*)
          INTO  :sql_count
          FROM  t_re_elig elig,
                t_re_pmp_assign assign
         WHERE  elig.sak_recip = :sql_sak_recip
           AND  :sql_dte_effective BETWEEN elig.dte_effective AND elig.dte_end
           AND  elig.cde_status1 <> 'H'
           AND  elig.sak_pub_hlth IN (1,3)
           AND  assign.sak_recip = elig.sak_recip
           AND  assign.sak_pgm_elig = elig.sak_pgm_elig;
    if (sqlca.sqlcode != ANSI_SQL_OK)
    {
        fprintf (stderr, "%s - ERROR: Could not get assignment count for sak_recip [%d]!\n", FNAME, sql_sak_recip);
        PrintSQLError();
        cleanUp();
        exit(FAILURE);
    }
    if ( sql_count == 0)
    {
        return mgd_FALSE;   //no assignment
    }
    return mgd_TRUE;    //has assignment
}

/*********************************************************************/
/*                                                                   */
/* Function Name: getBeneInfo                                        */
/*                                                                   */
/* Description: Retrieves beneficiary information for the report.    */
/*                                                                   */
/*********************************************************************/
static void getBeneInfo(void)
{
    sql_beneName[0] = '\0';
    sql_idMedicaid[0] = '\0';

    EXEC SQL SELECT
        RTRIM(nam_last, ' ') || ', ' || RTRIM(nam_first, ' ') || ' '
              || nam_mid_init nam_fmt1,
        id_medicaid
    INTO
        :sql_beneName,
        :sql_idMedicaid
    FROM t_re_base
    WHERE sak_recip = :sql_sak_recip;

    if (SQL_ERROR)
    {
        fprintf(stderr, "%s : Error, failed to get beneficiaries data."
                " OraError: %d\n", "getBeneInfo", sqlca.sqlcode);
        PrintSQLError();
        cleanUp();
        exit(EXIT_FAILURE);
    }
    return;
}

/*********************************************************************/
/*                                                                   */
/* Function Name: getLOC                                             */
/*                                                                   */
/* Description: Retrieves LOC information for the report.            */
/* Return: FUNCTION_SUCCESSFUL: Loc is in list for report.           */
/*         FUNCTION_FAILED: Not it list.                             */
/*********************************************************************/
static int getLOC(void)
{
    char FNAME[] = "getLOC";


    sql_cde_loc[0] = '\0';
    sql_loc_dte_effective = 0;
    sql_cde_liv_arng[0] = '\0';
    sql_kes_cde_loc[0] = '\0';

    EXEC SQL
        SELECT  cde_loc, dte_effective, cde_liv_arng, kes_cde_loc
          INTO  :sql_cde_loc,
                :sql_loc_dte_effective,
                :sql_cde_liv_arng,
                :sql_kes_cde_loc
         FROM   t_re_loc
        WHERE   sak_recip = :sql_sak_recip
          AND   :sql_dte_effective BETWEEN dte_effective AND dte_end
          AND   cde_loc IN ( '070', '071', '040', '041', '020', '021', '010', '011',
                             '180', '181', '030', '031', '250', '251', '310', '311',
                             '320', '321', '300', '301', '330', '331', '170', '171' )
          ;
    if (SQL_ERROR)
    {
        fprintf(stderr, "%s : Error, failed to get LOC data, sak_recip[%d], date[%d]\n",
                        FNAME, sql_sak_recip, sql_dte_effective);
        PrintSQLError();
        cleanUp();
        exit(EXIT_FAILURE);
    }
    else if (SQL_NOT_FOUND)
    {
        fprintf(stderr, "LOC did not qualify for this report: sak_recip: [%d]\n", sql_sak_recip);
        return FUNCTION_FAILED;
    }
    return FUNCTION_SUCCESSFUL;
}

