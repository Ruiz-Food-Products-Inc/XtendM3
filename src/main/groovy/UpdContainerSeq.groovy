/**
 * README
 * This transaction will attempt to make each balance id have unique container number
 *
 * Name: SplitContainer
 * Description: Splits
 * Date	      Changed By            Description
 * 20230214	  JHAGLER               initial development
 */

public class UpdContainerSeq extends ExtendM3Transaction {
  private final MIAPI mi
  private final MICallerAPI miCaller
  private final DatabaseAPI database
  private final LoggerAPI logger
  private final UtilityAPI utility

  public UpdContainerSeq(MIAPI mi, DatabaseAPI database, MICallerAPI miCaller, LoggerAPI logger, UtilityAPI utility) {
    this.mi = mi
    this.database = database
    this.miCaller = miCaller
    this.logger = logger
    this.utility = utility
  }

  public void main() {


    int CONO = mi.in.get('CONO') as int
    String WHLO = mi.in.get('WHLO').toString()
    String WHSL = mi.in.get('WHSL').toString()
    String ITNO = mi.in.get('ITNO').toString()
    String BANO = mi.in.get('BANO').toString()
    String CAMU = mi.in.get('CAMU').toString()

    if (!BANO || !CAMU) {
      // if lot number or container are not given, exit
      return
    }

    DBAction actionMITLOC = database
      .table("MITLOC")  // balance ids
      .index("99") // sort by CONO, CAMU
      .build()

    DBContainer containerMITLOC = actionMITLOC.getContainer()
    containerMITLOC.set("MLCONO", CONO)
    containerMITLOC.set("MLCAMU", CAMU)

    int keys = 2
    int records = actionMITLOC.readAll(containerMITLOC, keys, {})

    logger.debug("Found ${records} balance ids for container ${CAMU}")

    if (records > 1) {
      // container is not unique
      // add container number to this transaction to make it unique
      String nextCAMU = utility.call("ManageContainer", "GetNextContainerNumber", database, CONO, CAMU)

      logger.debug("Bal id WHLO=${WHLO}; ITNO=${ITNO}; WHSL=${WHSL}; BANO=${BANO}; CAMU=${CAMU} will be moved to ${nextCAMU}")
      def params = [
        "PRFL": "*EXE",
        "E0PA": "WS",
        "E065": "WMS",
        "WHLO": WHLO,
        "WHSL": WHSL,
        "ITNO": ITNO,
        "BANO": BANO,
        "CAMU": CAMU,
        "TWSL": WHSL,
        "TOCA": nextCAMU
      ]
      miCaller.call("MMS850MI", "AddMove", params, { Map<String, ?> resp ->
        if (resp.error) {
          mi.error(resp.get("errorMessage").toString())
        }
      })
    }

  }
}
