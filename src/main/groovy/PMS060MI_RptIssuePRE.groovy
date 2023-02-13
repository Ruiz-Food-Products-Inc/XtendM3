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
 */


public class PMS060MI_RptIssuePRE extends ExtendM3Trigger {

  private final TransactionAPI transaction
  private final DatabaseAPI database
  private final ProgramAPI program
  private final UtilityAPI utility
  private final LoggerAPI logger

  public PMS060MI_RptIssuePRE(
    TransactionAPI transaction,
    DatabaseAPI database,
    ProgramAPI program,
    UtilityAPI utility,
    LoggerAPI logger) {
    this.transaction = transaction
    this.database = database
    this.program = program
    this.utility = utility
    this.logger = logger
  }


  public void main() {

    String iCONO = transaction.parameters.get("CONO").toString()  // not sent from FT
    iCONO = iCONO == null || iCONO.trim().isEmpty() ? program.LDAZD.get("CONO").toString() : iCONO

    // on the following fields are sent from Factory Track api calls
    String iFACI = transaction.parameters.get("FACI").toString()
    String iMFNO = transaction.parameters.get("MFNO").toString()
    String iMSEQ = transaction.parameters.get("MSEQ").toString()
    String iWHSL = transaction.parameters.get("WHSL").toString()
    String iCAMU = transaction.parameters.get("CAMU").toString() // will be "base" container

    logger.debug("Transaction params: ${transaction.parameters}".toString())

    // get mo header additional data
    Map<String, ?> head = getHead(iCONO, iFACI, iMFNO)
    if (head == null) {
      transaction.abortTransaction("MFNO", "XRE0103", "Could not find MO ${iMFNO}".toString())
      return
    }
    String WHLO = head.get("WHLO")
    String PRNO = head.get("PRNO")

    // get mo material additional data
    Map<String, ?> material = getMaterial(iCONO, iFACI, PRNO, iMFNO, iMSEQ)
    if (material == null) {
      transaction.abortTransaction("MSEQ", "XRE0103", "Could not find material sequence ${iMSEQ} for  MO ${iMFNO}".toString())
      return
    }
    String ITNO = material.get("MTNO")
    String moWHSL = material.get("WHSL")


    if (!isValidLocation(iWHSL, moWHSL)) {
      transaction.abortTransaction("WHSL", "PM06009", "Material must be issued from ${moWHSL}".toString())
      return
    }


    logger.debug("Selecting conatiner for WHLO: ${WHLO}; ITNO: ${ITNO}; WHSL: ${iWHSL}; CAMU: ${iCAMU}".toString())
    // look up balance id with matching base container in the given location
    Map<String, ?> balId = utility.call("ManageContainer", "SelectBalanceID",
      database, iCONO, WHLO, ITNO, iWHSL, iCAMU) as Map<String, ?>

    String rptCAMU = balId.get("CAMU")
    String rptBANO = balId.get("BANO")
    logger.debug("Selected balance id:${balId}".toString())

    // factory track calls are not requiring a scan for BANO, set from selected container bal id
    transaction.parameters.put("BANO", rptBANO)
    transaction.parameters.put("CAMU", rptCAMU)


  }


  /**
   * Get MO header details
   * @param iCONO
   * @param iFACI
   * @param iMFNO
   * @return details for WHLO and PRNO
   */
  public Map<String, ?> getHead(String iCONO, String iFACI, String iMFNO) {
    Map<String, ?> head

    DBAction actionMWOHED = database.table("MWOHED").index("55").selection("VHWHLO", "VHPRNO").build()

    DBContainer containerMWOHED = actionMWOHED.getContainer()
    containerMWOHED.setInt("VHCONO", iCONO as int)
    containerMWOHED.setString("VHFACI", iFACI)
    containerMWOHED.setString("VHMFNO", iMFNO)

    int keys = 3
    int limit = 1
    actionMWOHED.readAll(containerMWOHED, keys, limit, { DBContainer c ->
      head = [:]
      head.put("WHLO", c.getString("VHWHLO"))
      head.put("PRNO", c.getString("VHPRNO"))
    })

    logger.debug("MO head = ${head}".toString())
    return head
  }


  /**
   * Sets material details
   * @param CONO
   * @param FACI
   * @param PRNO
   * @param MFNO
   * @param MSEQ
   * @return details with WHSL, MTNO
   */
  public Map<String, ?> getMaterial(String CONO, String FACI, String PRNO, String MFNO, String MSEQ) {
    Map<String, ?> material

    DBAction actionMWOMAT = database.table("MWOMAT").index("00").selection("VMWHSL", "VMMTNO").build()
    DBContainer containerMWOMAT = actionMWOMAT.createContainer()
    containerMWOMAT.setInt("VMCONO", CONO as int)
    containerMWOMAT.setString("VMFACI", FACI)
    containerMWOMAT.setString("VMPRNO", PRNO)
    containerMWOMAT.setString("VMMFNO", MFNO)
    containerMWOMAT.setInt("VMMSEQ", MSEQ as int)

    int keys = 5
    int limit = 1
    actionMWOMAT.readAll(containerMWOMAT, keys, limit, { DBContainer c ->
      material = [:]
      material.put("WHSL", c.getString("VMWHSL"))
      material.put("MTNO", c.getString("VMMTNO"))
    })

    logger.debug("MO material = ${material}".toString())
    return material
  }


  /**
   * Returns true if report location matches the mo material locations
   * @param rptWHSL
   * @param moWHSL
   * @return
   */
  public boolean isValidLocation(String rptWHSL, moWHSL) {
    logger.debug("Validating location  rptWHSL=${rptWHSL}  moWHSL:${moWHSL}")
    if (rptWHSL != moWHSL) {
      return false
    }
    logger.debug("Location is valid to report issue from")
    return true
  }
}
