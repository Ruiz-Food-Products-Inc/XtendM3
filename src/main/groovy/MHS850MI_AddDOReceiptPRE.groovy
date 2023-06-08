/**
 * README
 * This trigger will override the TOCA field to ensure that the
 * container number received at the destination warehouse exists on only one balance id
 *
 * Name: SplitContainer
 * Description: Splits
 * Date	      Changed By            Description
 * 20230214	  JHAGLER               initial development
 * 20230607   JHAGLER               only split containers if receiving into COMG=7,
 *                                  if COMG=1, remove suffix
 */

public class MHS850MI_AddDOReceiptPRE extends ExtendM3Trigger {


  private final LoggerAPI logger
  private final ProgramAPI program
  private final UtilityAPI utility
  private final TransactionAPI transaction
  private final DatabaseAPI database
  private final MICallerAPI miCaller

  private int CONO

  public MHS850MI_AddDOReceiptPRE(LoggerAPI logger,
                                  ProgramAPI program,
                                  UtilityAPI utility,
                                  TransactionAPI transaction,
                                  DatabaseAPI database,
                                  MICallerAPI miCaller) {
    this.logger = logger
    this.program = program
    this.utility = utility
    this.transaction = transaction
    this.database = database
    this.miCaller = miCaller

  }

  public void main() {

    String strCONO = transaction.parameters.get("CONO").toString()
    CONO = strCONO == null || strCONO.isEmpty() ? program.LDAZD.get("CONO") as int : strCONO as int

    String WHLO = transaction.parameters.get("WHLO").toString()
    String ITNO = transaction.parameters.get("ITNO").toString()
    String CAMU = transaction.parameters.get("CAMU").toString()

    if (!CAMU) {
      // exit if no container number is provided
     return
    }

    String COMG = getContainerManagementCode(WHLO, ITNO)
    if (COMG == "1") {
      // remove any container suffixes
      logger.debug("Container management is ${COMG} in warehouse ${WHLO}")
      if (CAMU.contains("_")) {
        String baseCAMU = CAMU.split("_")[0]
        logger.debug("Container ${CAMU} has a suffix, receiving ast ${baseCAMU}")
        transaction.parameters.put("TOCA", baseCAMU)
        return
      }
    }

    boolean hasMultiple = hasMultipleBalanceIds(CAMU)
    logger.debug("Container ${CAMU} has multiple balance ids = ${hasMultiple}")
    if (hasMultiple) {
      // this container exists in multiple locations
      // receive this into a newly sequenced container
      String nextCAMU = getNextContainerNumber(CAMU)
      if (nextCAMU) {
        logger.debug("Container will be received as ${nextCAMU}")
        transaction.parameters.put("TOCA", nextCAMU)
      } else {
        transaction.abortTransaction("", "", "Could not get unique container number to receive against.")
      }

    }

  }

  /**
   * Get next generated container number
   * @param WHLO
   * @return containerNumber
   */
  String getContainerManagementCode(String WHLO, String ITNO) {
    String containerManagementCode = null
    def params = [
      "WHLO": WHLO,
      "ITNO": ITNO
    ]

    miCaller.call("MMS200MI", "GetItmWhsBasic", params, {Map<String, ?> resp ->
      containerManagementCode = resp.get("COMG").toString()
    })

    return containerManagementCode
  }

  /**
   * Call utility to get next available container number
   * @param CAMU
   * @return
   */
  private String getNextContainerNumber(String CAMU) {
    String nextCAMU = null

    logger.debug("Getting next available container number")
    Object resp = utility.call("ManageContainer", "GetNextContainerNumber", database, CONO, CAMU)
    if (resp == void || resp == null) {
      return null
    }
    nextCAMU = resp as String

    logger.debug("Next conatiner number is ${nextCAMU}")
    return nextCAMU
  }

  /**
   * Get the count of records with the same container number
   * @param CAMU
   * @return boolean
   */
  private boolean hasMultipleBalanceIds(String CAMU) {
    DBAction actionMITLOC = database.table("MITLOC").index("99").build()
    DBContainer containerMITLOC = actionMITLOC.createContainer()
    containerMITLOC.setInt("MLCONO", CONO)
    containerMITLOC.setString("MLCAMU", CAMU)

    int keys = 2
    int limit = 2  // set limit to 2, only needing to see if there is MORE than 1
    int records = actionMITLOC.readAll(containerMITLOC, keys, limit, {})
    return records > 1
  }
}
