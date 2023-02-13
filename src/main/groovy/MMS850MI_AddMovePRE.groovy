/**
 * README
 * This trigger will generate a new container number when performing the move container function
 *
 * Name: MHS850MI_AddMovePRE
 * Description: Move and split containers
 * Date	      Changed By            Description
 * 20230207	  JHAGLER               initial development
 */
class MMS850MI_AddMovePRE extends ExtendM3Trigger {

  private final DatabaseAPI database
  private final ProgramAPI program
  private final UtilityAPI utility
  private final TransactionAPI transaction
  private final LoggerAPI logger

  MMS850MI_AddMovePRE(DatabaseAPI database, ProgramAPI program, UtilityAPI utility, TransactionAPI transaction, LoggerAPI logger) {
    this.database = database
    this.program = program
    this.utility = utility
    this.transaction = transaction
    this.logger = logger
  }


  void main() {
    String strCONO = transaction.parameters.get("CONO").toString()
    int CONO = strCONO == null || strCONO.isEmpty() ? program.LDAZD.get("CONO") as int : strCONO as int

    String CAMU = transaction.parameters.get("CAMU").toString()
    String TOCA = transaction.parameters.get("TOCA").toString()

    logger.debug("Input values: CONO=${CONO}; CAMU=${CAMU}; TOCA=${TOCA}")

    if (CAMU.isEmpty()) {
      // no container was provided to API input, exit here
      return
    }

    if (!TOCA.isEmpty()) {
      // to container was already filled out, do not override it, exit here
      return
    }

    // get the next available container number
    logger.debug("Getting next available container number")
    String nextCAMU = utility.call("ManageContainer", "GetNextContainerNumber", database, CONO, CAMU)

    logger.debug("Next conatiner number is ${nextCAMU}")
    transaction.parameters.put("TOCA", nextCAMU)

  }
}
