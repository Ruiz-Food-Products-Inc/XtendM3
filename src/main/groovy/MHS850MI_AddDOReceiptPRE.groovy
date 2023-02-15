/**
 * README
 * This trigger will override the TOCA field to ensure that the
 * container number received at the destination warehouse exists on only one balance id
 *
 * Name: SplitContainer
 * Description: Splits
 * Date	      Changed By            Description
 * 20230214	  JHAGLER               initial development
 */

public class MHS850MI_AddDOReceiptPRE extends ExtendM3Trigger {


  private final LoggerAPI logger
  private final ProgramAPI program
  private final UtilityAPI utility
  private final TransactionAPI transaction
  private final DatabaseAPI database

  private int CONO

  public MHS850MI_AddDOReceiptPRE(LoggerAPI logger,
                                  ProgramAPI program,
                                  UtilityAPI utility,
                                  TransactionAPI transaction,
                                  DatabaseAPI database) {
    this.logger = logger
    this.program = program
    this.utility = utility
    this.transaction = transaction
    this.database = database

  }

  public void main() {

    String strCONO = transaction.parameters.get("CONO").toString()
    CONO = strCONO == null || strCONO.isEmpty() ? program.LDAZD.get("CONO") as int : strCONO as int

    String CAMU = transaction.parameters.get("CAMU").toString()

    if (!CAMU) {
      // exit if no container number is provided
     return
    }

    int records = getCountOfBalanceIds(CAMU)
    logger.debug("Found ${records} balance ids with container ${CAMU}")
    if (records > 1) {
      // this container exists in multiple locations
      // receive this into a newly sequenced container
      String nextCAMU = utility.call("ManageContainer", "GetNextContainerNumber",
        database, CONO, CAMU)
      if (nextCAMU) {
        logger.debug("Container will be received as ${nextCAMU}")
        transaction.parameters.put("TOCA", nextCAMU)
      } else {
        transaction.abortTransaction("", "", "Could not get unique container number to receive against.")
      }

    }

  }

  /**
   * Get the count of records with the same container number
   * @param CAMU
   * @return count of records
   */
  int getCountOfBalanceIds(String CAMU) {
    DBAction actionMITLOC = database.table("MITLOC").index("99").build()
    DBContainer containerMITLOC = actionMITLOC.createContainer()
    containerMITLOC.setInt("MLCONO", CONO)
    containerMITLOC.setString("MLCAMU", CAMU)

    int keys = 2
    int records = actionMITLOC.readAll(containerMITLOC, keys, {})
    return records
  }
}
