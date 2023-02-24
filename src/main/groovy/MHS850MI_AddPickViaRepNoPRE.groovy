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
 * 20232323   JHAGLER            only select container number, do not split
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
    String CAMU = transaction.parameters.get("CAMU").toString()

    // by pass if no location or container was given
    if (!CAMU || !WHSL) {
      return
    }

    // find the item number for this pick reporting number
    Map<String, String> pickDetails = getPickDetails(CONO, PLRN)
    if (!pickDetails) {
      transaction.abortTransaction("PLRN", "WPL4003", "Could not find pick details for reporting number ${PLRN}")
      return
    }

    String ITNO = pickDetails.get("ITNO").toString()
    if (!ITNO) {
      transaction.abortTransaction("", "WWH0103", "Could not find item from reporting number ${PLRN}")
      return
    }


    // the input container number may be a "base" container number, select the best matching actual container number
    Map<String, ?> selectedBalId = selectContainer(CONO, WHLO, ITNO, WHSL, CAMU)
    if (!selectedBalId) {
      transaction.abortTransaction("", "WWH0103", "No container was selected for CONO:${CONO}, WHLO:${WHLO}, WHSL:${WHSL}, CAMU:${CAMU}")
      return
    }

    String selectedCAMU = selectedBalId.get("CAMU").toString()
    if (!selectedCAMU) {
      transaction.abortTransaction("", "WWH0103", "No container was selected for CONO:${CONO}, WHLO:${WHLO}, WHSL:${WHSL}, CAMU:${CAMU}")
      return
    }

    // update the container number for the transaction parameter
    transaction.parameters.put("CAMU", selectedCAMU)

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
    DBAction action = database.table("MITALO").index("50").selection("MQITNO").build()
    DBContainer container = action.createContainer()
    container.setInt("MQCONO", CONO)
    container.setLong("MQPLRN", PLRN)

    int keys = 2
    int limit = 1
    action.readAll(container, keys, limit, { DBContainer c ->
      details = [
        "ITNO": c.get("MQITNO").toString()
      ]
    })

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

    return balId
  }


}
