
/**
 * README
 * This trigger will generate a new container number if container managment is set to 1
 *
 * Name: PPS100MI_ReceiptPRE
 * Description: Splits
 * Date	      Changed By            Description
 * 20230607	  JHAGLER               initial development
 */

public class PPS001MI_ReceiptPRE extends ExtendM3Trigger {
  private final TransactionAPI transaction
  private final MICallerAPI miCaller
  private final LoggerAPI logger
  private final DatabaseAPI database
  private final ProgramAPI program

  private int CONO

  public PPS001MI_ReceiptPRE(TransactionAPI transaction, MICallerAPI miCaller, DatabaseAPI database, ProgramAPI program, LoggerAPI logger) {
    this.transaction = transaction
    this.miCaller = miCaller
    this.database = database
    this.program = program
    this.logger = logger
  }

  public void main() {

    CONO = program.LDAZD.CONO as int

    String WHLO = transaction.parameters.get("WHLO")
    String PUNO = transaction.parameters.get("PUNO")
    String PNLI = transaction.parameters.get("PNLI")
    String CAMU = transaction.parameters.get("CAMU")

    String ITNO = getItemNumber(PUNO, PNLI)
    logger.debug("ITNO is ${ITNO}")

    if (ITNO != null) {

      if (CAMU.isEmpty()) {
        String containerManagementMethod = getContainerManagementCode(WHLO, ITNO)
        if (containerManagementMethod == "1") {
          logger.debug("COMG is ${containerManagementMethod}")

          String containerNumber = getNextContainer(WHLO)
          logger.debug("containerNumber is ${containerNumber}")
          if (containerNumber != null) {
            transaction.parameters.put("CAMU", containerNumber)
          }
        }
      }

    }

  }


  /**
   * Get item number from PO and line
   * @param PUNO
   * @param PNLI
   * @return ITNO
   */
  String getItemNumber(String PUNO, String PNLI) {
    String itemNumber = null
    // def params = [
    //   "PUNO": PUNO,
    //   "PNLI": PNLI,
    // ]

    // logger.debug("PUNO=${PUNO}; PNLI=${PNLI}")

    // miCaller.call("PPS200MI", "GetLine", params, {Map<String, ?> resp ->
    //   if (resp.error) {
    //     logger.debug("${resp}")
    //   } else {
    //     itemNumber = resp.get("ITNO").toString()
    //   }
    // })

    DBAction actionMPLINE = database.table("MPLINE").index("00").selection("IBITNO").build()
    DBContainer containerMPLINE = actionMPLINE.getContainer()
    containerMPLINE.setInt("IBCONO", CONO)
    containerMPLINE.set("IBPUNO", PUNO)
    containerMPLINE.setInt("IBPNLI", PNLI as int)
    if (actionMPLINE.read(containerMPLINE)) {
      itemNumber = containerMPLINE.get("IBITNO").toString()
    }

    return itemNumber
  }


  /**
   * Get next generated container number
   * @param WHLO
   * @return containerNumber
   */
  String getNextContainer(String WHLO) {
    String containerNumber = null
    def params = [
      "WHLO": WHLO,
    ]

    miCaller.call("EXT165MI", "RtvNextCont", params, {Map<String, ?> resp ->
      containerNumber = resp.get("CAMU").toString()
      if (resp.error) {
        logger.debug("${resp}")
      }
    })

    return containerNumber
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


}
