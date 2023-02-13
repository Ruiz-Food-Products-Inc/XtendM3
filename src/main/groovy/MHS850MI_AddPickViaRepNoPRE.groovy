/**
 * README
 * this extension is to support a use case in factory track
 * when reporting against a pick list, the barcode only has the "base" container
 * but the actual container to be picked may exist as as sub container
 * this extension will search for matching sub containers and substitute that value in
 * the input parameters that the standard AddPickViaRepNo transaction will process
 *
 *
 * Name: MHS850MI_AddPickViaRepNoPRE
 * Description: MHS850MI_AddPickViaRepNoPRE
 * Date	      Changed By         Description
 * 20230212	  JHAGLER            initial development
 **/


public class MHS850MI_AddPickViaRepNoPRE extends ExtendM3Trigger {


  private final LoggerAPI logger
  private final ProgramAPI program
  private final UtilityAPI utility
  private final TransactionAPI transaction
  private final MICallerAPI miCaller
  private final DatabaseAPI database

  public MHS850MI_AddPickViaRepNoPRE(LoggerAPI logger,
                                     MICallerAPI miCaller,
                                     ProgramAPI program,
                                     UtilityAPI utility,
                                     TransactionAPI transaction,
                                     DatabaseAPI database) {
    this.logger = logger
    this.program = program
    this.utility = utility
    this.transaction = transaction
    this.miCaller = miCaller
    this.database = database
  }


  public void main() {

    String strCONO = transaction.parameters.get("CONO").toString()
    int CONO = strCONO == null || strCONO.isEmpty() ? program.LDAZD.get("CONO") as int : strCONO as int

    long PLRN = transaction.parameters.get("PLRN") as long
    String WHLO = transaction.parameters.get("WHLO").toString()
    String WHSL = transaction.parameters.get("WHSL").toString()
    String BANO = transaction.parameters.get("BANO").toString()
    String CAMU = transaction.parameters.get("CAMU").toString()
    double QTYP = transaction.parameters.get("QTYP") as double

    // find the item number for this pick reporting number
    Map<String, String> pickDetails = getPickDetails(CONO, PLRN)
    String ITNO = pickDetails.get("ITNO").toString()
    String TTYP = pickDetails.get("TTYP").toString()

    // the input container number may be a "base" container number, select the best matching actual container number
    Map<String, ?> selectedBalId = selectContainer(CONO, WHLO, ITNO, WHSL, CAMU)
    String selectedCAMU = selectedBalId.get("CAMU").toString()
    double STQT = selectedBalId.get("STQT") as double
    double ALQT = selectedBalId.get("ALQT") as double
    double allocableQty = STQT - ALQT

    String finalCAMU = selectedCAMU

    if (shouldSplit(STQT, QTYP)) {
      // returns the new container that quantity was split to
      String nextCAMU = getNextContainer(CONO, selectedCAMU)
      if (allocableQty < QTYP) {
        // need to deallocate before splitting
        deallocate(WHLO, ITNO, WHSL, BANO, selectedCAMU, QTYP, TTYP)
      }
      splitContainer(CONO, WHLO, ITNO, WHSL, BANO, selectedCAMU, QTYP, nextCAMU)
      finalCAMU = nextCAMU
    }

    // update the container number for the transaction parameter
    transaction.parameters.put("CAMU", finalCAMU)

  }


  /**
   * Get the pick details for the pick list reporting number
   * @param CONO
   * @param PLRN
   * @return
   */
  private Map<String, String> getPickDetails(int CONO, long PLRN) {
    // NOTE MWS422MI.GetPickByRepNo was NOT returning a valid result  - using DB access instead

    logger.debug("Getting item number from pick list reporting number ${PLRN}".toString())

    Map<String, String> details = null
    DBAction action = database.table("MITALO").index("50").selection("MQITNO", "MQTTYP").build()
    DBContainer container = action.createContainer()
    container.setInt("MQCONO", CONO)
    container.setLong("MQPLRN", PLRN)

    int keys = 2
    int limit = 1
    action.readAll(container, keys, limit, { DBContainer c ->
      details = [
        "ITNO": c.get("MQITNO").toString(),
        "TTYP": c.get("MQTTYP").toString()
      ]
    })

    if (!details) {
      transaction.abortTransaction("PLRN", "WPL4003", "Could not retrieve pick details")
    }

    logger.debug("Pick details retrieved: ${details}")

    return details
  }

  /**
   * Find a specific balance id by searching with a "base" container
   * @param CONO
   * @param WHLO
   * @param ITNO
   * @param WHSL
   * @param CAMU
   * @return bal id details
   */
  private Map<String, ?> selectContainer(int CONO, String WHLO, String ITNO, String WHSL, String CAMU) {
    logger.debug("Selecting container for CONO:${CONO}, WHLO:${WHLO}, ITNO:${ITNO}; WHSL:${WHSL}, CAMU:${CAMU}".toString())

    Map<String, ?> balId = utility.call("ManageContainer", "SelectBalanceID", database,
      CONO, WHLO, ITNO, WHSL, CAMU) as Map<String, ?>

    logger.debug("Container selected: ${balId}")

    if (!balId) {
      transaction.abortTransaction("", "WWH0103", "No container was selected for CONO:${CONO}, WHLO:${WHLO}, WHSL:${WHSL}, CAMU:${CAMU}".toString())
    }

    return balId
  }


  /**
   * Determine if the picking activity should be split to a new container before actually reporting the pick
   * @param onHandBalance
   * @param qtyToPick
   * @return
   */
  private boolean shouldSplit(double onHandBalance, double qtyToPick) {
    logger.debug("Checking to see if balance id should be split before picking")

    if (onHandBalance == qtyToPick) {
      logger.debug("Qty to pick is for the full balance id on hand, no need to split")
      return false
    } else if (onHandBalance > qtyToPick) {
      logger.debug("Qty to pick is not for the full balance id on hand, need to split before picking")
      return true
    } else {
      transaction.abortTransaction("", "", "Qty to pick is less than on hand balance")
    }
  }


  /**
   * Deallocate by calling MMS120MI.UpdDetAlloc
   * @param WHLO
   * @param ITNO
   * @param WHSL
   * @param BANO
   * @param CAMU
   * @param ALQT
   * @param TTYP
   * @return
   */
  private deallocate(String WHLO, String ITNO, String WHSL, String BANO, String CAMU, double ALQT, String TTYP) {
    logger.debug("Deallocating")

    def params = [
      "WHLO": WHLO,
      "ITNO": ITNO,
      "WHSL": WHSL,
      "BANO": BANO,
      "CAMU": CAMU,
      "ALQT": ALQT.toString(),
      "TTYD": TTYP  // transaction type to deallocate
    ]
    logger.debug("Calling MMS120MI/UpdDetAlloc with ${params}")

    miCaller.call("MMS120MI", "UpdDetAlloc", params, { Map<String, ?> resp ->
      if (resp.get("error")) {
        logger.error("Error deallocating container: ${resp.get("errorMessage")}".toString())
      } else {
        logger.debug("Qty was deallocated successfully".toString())
      }
    })
  }


  /**
   * Call the utility method to get the next available container number
   * @param CONO
   * @param CAMU
   * @return
   */
  private String getNextContainer(int CONO, String CAMU) {
    logger.debug("Getting next container number for split")
    String nextContainer = utility.call("ManageContainer", "GetNextContainerNumber",
      database, CONO, CAMU)
    logger.debug("Next container number will be ${nextContainer}".toString())
    return nextContainer

  }

  /**
   * Split the balance id to a new container
   * @param CONO
   * @param WHLO
   * @param ITNO
   * @param WHSL
   * @param BANO
   * @param CAMU
   * @param QTYP
   * @param TOCA
   */
  private void splitContainer(int CONO, String WHLO, String ITNO, String WHSL, String BANO, String CAMU, double QTYP, String TOCA) {
    logger.debug("Splitting container")

    def params = [
      "PRFL": "*EXE",
      "CONO": CONO.toString(),
      "E0PA": "WS",
      "E065": "WMS",
      "WHLO": WHLO,
      "WHSL": WHSL,
      "ITNO": ITNO,
      "BANO": BANO,
      "CAMU": CAMU,
      "QLQT": QTYP.toString(),
      "TWSL": WHSL,
      "TOCA": TOCA
    ]
    logger.debug("Calling MMS850MI/AddMove with ${params}")

    miCaller.call("MMS850MI", "AddMove", params, { Map<String, ?> resp ->
      if (resp.get("error")) {
        logger.error("Error splitting container: ${resp.get("errorMessage")}".toString())
      } else {
        logger.debug("Container ${CAMU} was split to ${TOCA}".toString())
      }
    })
  }

}
