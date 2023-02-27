/**
 * README
 * This PRE trigger will allow a user to scan in a "base" container and report against
 * a matched "sub" container from that location
 *
 * Also, the issue from location is validated against the location specific on the MO
 * material line
 *
 * Name: PMS060MI_RptIssuePRE
 * Description: Report issue PRE trigger
 * Date	      Changed By            Description
 * 20230209	  JHAGLER               initial development
 * 20230225	  JHAGLER               improve error handling
 */


class PMS060MI_RptIssuePRE extends ExtendM3Trigger {

  private final TransactionAPI transaction
  private final DatabaseAPI database
  private final ProgramAPI program
  private final MICallerAPI miCaller
  private final UtilityAPI utility
  private final LoggerAPI logger

  private int CONO

  PMS060MI_RptIssuePRE(
    TransactionAPI transaction,
    DatabaseAPI database,
    ProgramAPI program,
    MICallerAPI miCaller,
    UtilityAPI utility,
    LoggerAPI logger) {
    this.transaction = transaction
    this.database = database
    this.program = program
    this.miCaller = miCaller
    this.utility = utility
    this.logger = logger
  }


  void main() {

    CONO = getCompany()
    if (!CONO) {
      logger.debug("Company could not be found.")
      return
    }

    Map<String, ?> parameters = getAndValidateParameters()
    if (parameters == null) {
      // valid parameters were not provided, exit
      return
    }
    logger.debug("Valid parameters were provided: ${parameters}")

    String FACI = parameters.get("FACI").toString()
    String MFNO = parameters.get("MFNO").toString()
    int MSEQ = parameters.get("MSEQ") as int
    String WHSL = parameters.get("WHSL").toString()
    String CAMU = parameters.get("CAMU").toString()

    // get mo header additional data
    Map<String, String> head = getHead(FACI, MFNO)
    if (head == null) {
      transaction.abortTransaction("MFNO", "XRE0103", "")  // record does not exist
      return
    }

    String WHLO = head.get("WHLO")
    String PRNO = head.get("PRNO")

    // get mo material additional data
    Map<String, String> material = getMaterial(FACI, PRNO, MFNO, MSEQ)
    if (material == null) {
      transaction.abortTransaction("MSEQ", "WMS0201", MSEQ.toString())  // Sequence number & is invalid
      return
    }
    String ITNO = material.get("MTNO")
    String moWHSL = material.get("WHSL")


    if (!isValidLocation(WHSL, moWHSL)) {
      transaction.abortTransaction("WHSL", "WWS0101", "${WHSL}") // Location &1 is invalid
      return
    }

    Map<String, String> selectedBalanceId = getSelectedBalanceId(WHLO, ITNO, WHSL, CAMU)
    if (selectedBalanceId == null) {
      transaction.abortTransaction("CAMU", "WLOCA03", "location=${WHSL} and container=${CAMU}")  // Balance identity &1 does not exist
      return
    }

    String selBANO = selectedBalanceId.get("BANO")
    String selCAMU = selectedBalanceId.get("CAMU")

    logger.debug("Overriding transaction parameters with BANO=${selBANO} and CAMU=${selCAMU}")

    // factory track calls are not requiring a scan for BANO, set from selected container bal id
    transaction.parameters.put("BANO", selBANO)
    transaction.parameters.put("CAMU", selCAMU)

  }


  /**
   * Gets company from first the transaction, then if not present, the program
   * @return
   */
  private int getCompany() {
    int CONO = 0

    // retrieve CONO from transaction
    String trsCONO = transaction.parameters.get("CONO").toString()
    logger.debug("trsCONO is ${trsCONO}")
    if (trsCONO != null) {
      if (trsCONO.isInteger()) {
        CONO = trsCONO as int
      }
    }

    // CONO not found from transaction, retrieve from program
    if (!CONO) {
      String pgmCONO = program.LDAZD.get("CONO").toString()
      logger.debug("pgmCONO is ${pgmCONO}")
      if (pgmCONO != null) {
        if (pgmCONO.isInteger()) {
          CONO = pgmCONO as int
        }
      }
    }

    logger.debug("CONO is ${CONO}")

    return CONO
  }

  private Map<String, ?> getAndValidateParameters() {
    Map<String, ?> parameters = null

    String FACI = transaction.parameters.get("FACI").toString()
    if (FACI == null || FACI.isEmpty()) {
      logger.debug("Transaction parameter FACI was null or empty")
      return null
    }

    String MFNO = transaction.parameters.get("MFNO").toString()
    if (MFNO == null || MFNO.isEmpty()) {
      logger.debug("Transaction parameter MFNO was null or empty")
      return null
    }

    String strMSEQ = transaction.parameters.get("MSEQ").toString()
    if (strMSEQ == null || !strMSEQ.isInteger()) {
      logger.debug("Transaction parameter MSEQ was null or is not an integer")
      return null
    }
    int MSEQ = strMSEQ as int

    String WHSL = transaction.parameters.get("WHSL").toString()
    if (WHSL == null || WHSL.isEmpty()) {
      logger.debug("Transaction parameter WHSL was null or empty")
      return null
    }

    String CAMU = transaction.parameters.get("CAMU").toString() // will be "base" container
    if (CAMU == null || CAMU.isEmpty()) {
      logger.debug("Transaction parameter CAMU was null or empty")
      return null
    }

    parameters = [
      "FACI": FACI,
      "MFNO": MFNO,
      "MSEQ": MSEQ,
      "WHSL": WHSL,
      "CAMU": CAMU
    ]

    return parameters
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
 * Calls PMS100MI.GetMatLine to retrieve material details
 * @param CONO
 * @param FACI
 * @param PRNO
 * @param MFNO
 * @param MSEQ
 * @return
 */
  private Map<String, String> getMaterial(String FACI, String PRNO, String MFNO, int MSEQ) {
    Map<String, String> material = null

    logger.debug("Looking up material for MSEQ=${MSEQ}")

    def params = [
      FACI: FACI,
      PRNO: PRNO,
      MFNO: MFNO,
      MSEQ: MSEQ.toString()
    ]
    miCaller.call("PMS100MI", "GetMatLine", params, { Map<String, ?> resp ->
      if (resp.error) {
        logger.debug("Error calling PMS100MI.GetMatLine: ${resp.errorMessage}")

      } else {
        String WHSL = resp.get("WHSL").toString()
        WHSL = WHSL ? WHSL.trim() : null

        String MTNO = resp.get("MTNO").toString()
        MTNO = MTNO ? MTNO.trim() : null

        if (WHSL && MTNO) {
          material = [
            "WHSL": WHSL,
            "MTNO": MTNO
          ]
        }

      }
    })

    if (material == null) {
      logger.debug("Material not found for MSEQ=${MSEQ}")
    } else {
      logger.debug("Material found for MSEQ=${MSEQ} was ${material}")
    }

    return material
  }


/**
 * Returns true if report location matches the mo material locations
 * @param rptWHSL
 * @param moWHSL
 * @return
 */
  private boolean isValidLocation(String rptWHSL, moWHSL) {
    logger.debug("Validating location  rptWHSL=${rptWHSL}  moWHSL:${moWHSL}")
    if (rptWHSL != moWHSL) {
      return false
    }
    logger.debug("Location is valid to report issue from")
    return true
  }

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
