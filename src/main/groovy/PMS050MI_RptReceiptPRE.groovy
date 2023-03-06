/**
 * README
 * This transaction will evaluate what would happen should backflush transactions
 * be report.
 *
 * Name: PMS050MI.RptReceiptPRE
 * Description: Check backflush status before allowing report receipt
 * Date	      Changed By            Description
 * 20230209	  JHAGLER               initial development
 * 20230302   JHAGLER               perform bal id selection if reversing
 */
public class PMS050MI_RptReceiptPRE extends ExtendM3Trigger {

  private final TransactionAPI transaction
  private final ProgramAPI program
  private final DatabaseAPI database
  private final MICallerAPI miCaller
  private final UtilityAPI utility
  private final LoggerAPI logger

  private int CONO

  PMS050MI_RptReceiptPRE(TransactionAPI transaction, ProgramAPI program, DatabaseAPI database, MICallerAPI miCaller, UtilityAPI utility, LoggerAPI logger) {
    this.transaction = transaction
    this.program = program
    this.database = database
    this.miCaller = miCaller
    this.utility = utility
    this.logger = logger
  }

  void main() {

    CONO = program.LDAZD.CONO as int

    String FACI = transaction.parameters.get("FACI").toString()
    String MFNO = transaction.parameters.get("MFNO").toString()
    String CAMU = transaction.parameters.get("CAMU").toString()
    String WHSL = transaction.parameters.get("WHSL").toString()
    double RPQA = transaction.parameters.get("RPQA") as double

    if (RPQA > 0) {
      // reporting receipt
      String status = getBackflushStatus(FACI, MFNO, RPQA)
      if (status != 'OK') {
        transaction.abortTransaction("RPQA", "PM06020", status)
        return
      }

    } else {
      // reversing a receipt
      // when reversing a receipt a "base" container may be passed in
      if (!CAMU || !WHSL) {
        // container and location are required to lookup a base container
        return
      }

      Map<String, String> head = getHead(FACI, MFNO)
      if (head == null) {
        transaction.abortTransaction("MFNO", "XRE0103", "")  // record does not exist
        return
      }

      String WHLO = head.get("WHLO")
      String PRNO = head.get("PRNO")

      Map<String, String> selectedBalanceId = getSelectedBalanceId(WHLO, PRNO, WHSL, CAMU)
      if (selectedBalanceId == null) {
        transaction.abortTransaction("CAMU", "WLOCA03", "${WHSL} / ${CAMU}")  // Balance identity &1 does not exist
        return
      }

      String selCAMU = selectedBalanceId.get("CAMU")

      logger.debug("Overriding transaction parameters with CAMU=${selCAMU}")

      transaction.parameters.put("CAMU", selCAMU)


    }



  }

  /**
   * Get backflush status from EXT050MI/GetBackflushSts
   * @param FACI
   * @param MFNO
   * @param RPQA
   * @return message
   */
  String getBackflushStatus(String FACI, String MFNO, double RPQA) {
    String status = null
    def params = [
      "FACI": FACI,
      "MFNO": MFNO,
      "RPQA": RPQA.toString()
    ]

    miCaller.call("EXT050MI", "GetBackflushSts", params, {Map<String, ?> resp ->
      status = resp.get("STAT").toString()
    })

    return status
  }


  /**
   * Read MWOHED55 to retrieve MO header details
   * @param CONO
   * @param MFNO
   * @return
   */
  private Map<String, String> getHead(String FACI, String MFNO) {
    Map<String, String> head = null

    logger.debug("Looking up header for MFNO=${MFNO}")

    DBAction actionMWOHED = database
      .table("MWOHED")
      .index("55")  // sort by CONO, FACI, MFNO
      .selection("VHWHLO", "VHPRNO")
      .build()

    DBContainer containerMWOHED = actionMWOHED.getContainer()
    containerMWOHED.setInt("VHCONO", CONO)
    containerMWOHED.setString("VHFACI", FACI)
    containerMWOHED.setString("VHMFNO", MFNO)

    int keys = 3
    int limit = 1
    actionMWOHED.readAll(containerMWOHED, keys, limit, { DBContainer c ->

      String WHLO = c.get("VHWHLO").toString()
      WHLO = WHLO ? WHLO.trim() : null

      String PRNO = c.get("VHPRNO").toString()
      PRNO = PRNO ? PRNO.trim() : null

      if (WHLO && PRNO) {
        head = [
          "WHLO": WHLO,
          "PRNO": PRNO
        ]
      }

    })


    if (head == null) {
      logger.debug("Header not found for MFNO=${MFNO}")
    } else {
      logger.debug("Header found for MFNO=${MFNO} was ${head}")
    }

    return head
  }


  /**
   * Calls utility to retrieve a selected matching container
   * @param WHLO
   * @param ITNO
   * @param WHSL
   * @param CAMU
   * @return
   */
  private Map<String, String> getSelectedBalanceId(String WHLO, String ITNO, String WHSL, String CAMU) {

    Map<String, String> selectedBalanceId = null

    // look up balance id with matching base container in the given location
    logger.debug("Selecting container for WHLO: ${WHLO}; ITNO: ${ITNO}; WHSL: ${WHSL}; CAMU: ${CAMU}")
    def resp = utility.call("ManageContainer", "SelectBalanceID",
      database, CONO, WHLO, ITNO, WHSL, CAMU)

    // utility call may return void or null
    if (resp == void || resp == null) {
      return null
    }

    Map<String, ?> map = resp as Map<String, ?>

    String selCAMU = map.get("CAMU").toString()
    String selBANO = map.get("BANO").toString()

    // validate both CAMU and BANO are returned
    if (selCAMU && selBANO) {
      selectedBalanceId = [
        "CAMU": selCAMU,
        "BANO": selBANO
      ]
    }

    return selectedBalanceId
  }

}
