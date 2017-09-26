# HP Port 11517 20100115 - From pendingcheckin/temp/libmgd_kaecses.so.mak 1.4
# HP Port 11517 20100115 - From libmgd_kaecses.so.mak version 1.4
#pragma ident "@(#)libmgd_kaecses.so.mak	1.6 09/23/12 EDS"

include dsksdflt.mak

#DEBUG=-g
#DEBUG += -DMGD_TRACE
MFLAGS=+z

SRCS=mgd_kaecses.sc
OBJS=$(UOBJ)/mgd_kaecses.o
DSKS_LIBS=-lmgd_common -lmgd_letters -lmgd_kaecses_kc

$(UBIN)/libmgd_kaecses.so:	$(OBJS)

# $(UOBJ)/mgd_kaecses.o:			mgd_kaecses.c

$(SRCS):
	sccs get src/$@
