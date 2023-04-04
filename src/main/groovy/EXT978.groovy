import java.time.LocalDate
import java.time.format.DateTimeFormatter
/**
 * README
 * This batch program will reclassify expired balance ids to status 1.
 * This is a replacement of the standard MMS978 that reclassifies to status 3.
 *
 * Name: EXT978
 * Description: Reclassify expired balance ids
 * Date	      Changed By            Description
 * 20230311	  JHAGLER               initial development
 */
class EXT978 extends ExtendM3Batch {
  private final BatchAPI batch
  private final DatabaseAPI database
  private final MICallerAPI miCaller
  private final LoggerAPI logger
  private final ProgramAPI program

  private int CONO
  private int CUDT

  private Set<String> lotsReclassed = new HashSet<String>()
  private Map<String, String> itemsWithItemTypes = new HashMap<String, String>()

  EXT978(BatchAPI batch, DatabaseAPI database, MICallerAPI miCaller, LoggerAPI logger, ProgramAPI program) {
    this.batch = batch
    this.database = database
    this.miCaller = miCaller
    this.logger = logger
    this.program = program
  }

  void main() {

    CONO = program.LDAZD.CONO as int
    CUDT = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInteger()

    logger.debug("Running batch program EXT978 to reclassify expired balance ids to status 1")
    logger.debug("Executing for company ${CONO} with current date ${CUDT}")



    // select all status 2-approved balance ids with today's expiration date
    ExpressionFactory exp = database.getExpressionFactory("MITLOC")
    exp = exp.eq("MLSTAS", "2")
    DBAction actionMITLOC = database.table("MITLOC")
      .index("15")  // CONO, ITNO, BANO, WHLO, WHSL, CAMU, REPN
      .matching(exp)
      .selection("MLFACI", "MLSTQT", "MLALOC")
      .build()
    DBContainer containerMITLOC = actionMITLOC.createContainer()
    containerMITLOC.setInt("MLCONO", CONO)

    int keys = 1

    actionMITLOC.readAll(containerMITLOC, keys, {DBContainer c ->
      String FACI = c.get("MLFACI").toString()
      String WHLO = c.get("MLWHLO").toString()
      String ITNO = c.get("MLITNO").toString()
      String WHSL = c.get("MLWHSL").toString()
      String BANO = c.get("MLBANO").toString()
      String CAMU = c.get("MLCAMU").toString()
      double STQT = c.getDouble("MLSTQT")
      double ALQT = c.getDouble("MLALQT")

      logger.debug("WHLO=${WHLO}; ITNO=${ITNO}; WHSL=${WHSL}; BANO=${BANO}; CAMU=${CAMU}; STQT=${STQT}; ALQT=${ALQT} ")
      if (STQT == 0) {
        logger.debug("On hand quantity is not zero.  Skip.")
      } else if (ALQT != 0) {
        logger.debug("Allocated quantity is not zero. Skip.")
      } else {
        int EXPI = getLotExpirationDate(ITNO, BANO)
        logger.debug("Lot expiration date is ${EXPI}")
        if (EXPI == 0) {
          logger.debug("Lot does not have an expiration date.  Skip.")
        } else if (EXPI >= CUDT) {
          logger.debug("Lot is not expired yet.  Skip.")
        } else {

          logger.debug("WHLO=${WHLO}; ITNO=${ITNO}; WHSL=${WHSL}; BANO=${BANO}; CAMU=${CAMU}; STQT=${STQT}; ALQT=${ALQT} ")
          logger.debug("Reclassifying balance id")
          reclass(WHLO, ITNO, WHSL, BANO, CAMU)

          if (lotsReclassed.contains(BANO)) {
            logger.debug("Lot ${BANO} has already been processed and does not need another QI request.")
          } else {
            // if item type is IG OR SG, add QI request
            String ITTY = getItemType(ITNO)
            if (ITTY == 'IG' || ITTY == 'SG') {
              logger.debug("Adding QI request for lot ${BANO}.")
              addQIRequest(FACI, ITNO, BANO)
              lotsReclassed.add(BANO)
            } else {
              logger.debug("Item type is ${ITTY} an does not require a QI request")
            }
          }

        }
      }

    })

  }

  /**
   * Get the expiration date for the item and lot number
   * @param ITNO
   * @param BANO
   * @return EXPI
   */
  private int getLotExpirationDate(String ITNO, String BANO) {
    int EXPI
    DBAction actionMILOMA = database.table("MILOMA")
      .index("00")  // CONO, ITNO, BANO
      .selection("LMEXPI")
      .build()
    DBContainer containerMILOMA = actionMILOMA.createContainer()
    containerMILOMA.setInt("LMCONO", CONO)
    containerMILOMA.setString("LMITNO", ITNO)
    containerMILOMA.setString("LMBANO", BANO)

    actionMILOMA.read(containerMILOMA)

    EXPI = containerMILOMA.getInt("LMEXPI")

    return EXPI
  }

  /**
   * Calls MMS850MI/AddRclLotSts with STAT=1
   * @param WHLO
   * @param ITNO
   * @param WHSL
   * @param BANO
   * @param CAMU
   */
  private reclass(String WHLO, String ITNO, String WHSL, String BANO, String CAMU) {

    def params = [
      "PRFL": "*AUT",
      "E0PA": "WS",
      "E065": "WMS",
      "WHLO": WHLO,
      "ITNO": ITNO,
      "WHSL": WHSL,
      "BANO": BANO,
      "CAMU": CAMU,
      "BREM": "000EXP:",  // added per QA
      "ALOC": "0",
      "STAS": "1"
    ]
    logger.debug("Calling MMS850MI/AddRclLotSts with ${params}")
    miCaller.call("MMS850MI", "AddRclLotSts", params, {Map<String, ?> resp ->
      if (resp.error) {
        logger.debug("Error calling MMS850MI/AddRclLotSts: ${resp.errorMessage}")
      }
    })
  }

  /**
   * Get the item type for an item number
   * @param ITNO
   * @return ITTY
   */
  private String getItemType(String ITNO) {
    String ITTY = itemsWithItemTypes.get(ITNO)
    if (ITTY == null) {

      def params = [
        "ITNO": ITNO
      ]
      logger.debug("Calling MMS200MI/Get with ${params}")
      miCaller.call("MMS200MI", "Get", params, {Map<String, ?> resp ->
        if (resp.error) {
          logger.debug("Error calling MMS200MI/Get: ${resp.errorMessage}")
        } else {
          ITTY = resp.get("ITTY").toString()
          itemsWithItemTypes.put(ITNO, ITTY)
        }
      })
    }

    return ITTY
  }

  /**
   * Adds QI request
   * @param FACI
   * @param ITNO
   * @param BANO
   */
  private addQIRequest(String FACI, String ITNO, String BANO) {

    def params = [
      "FACI": FACI,
      "ITNO": ITNO,
      "BANO": BANO,
      "QRDT": this.CUDT.toString(),
      "SRTT": "1",  // retest
      "TPS1": "0"  // test at pre-shipment
    ]
    logger.debug("Calling QMS300MI/AddQIRequest with ${params}")
    miCaller.call("QMS300MI", "AddQIRequest", params, {Map<String, ?> resp ->
      if (resp.error) {
        logger.debug("Error calling QMS300MI/AddQIRequest: ${resp.errorMessage}")
      }
    })
  }

}
