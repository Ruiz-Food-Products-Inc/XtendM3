/**
 * README
 * This extension allows users to provide a "base" container number to reverse a material issue
 *
 *
 *
 * Name: PMS060MI_DltIssuePRE
 * Description: Reverse material issue with base container
 * Date	      Changed By            Description
 * 20230209	  JHAGLER               initial development
 */


public class PMS060MI_DltIssuePRE extends ExtendM3Trigger {

  private final TransactionAPI transaction
  private final ProgramAPI program
  private final DatabaseAPI database
  private final LoggerAPI logger
  int CONO

  public PMS060MI_DltIssuePRE(TransactionAPI transaction, ProgramAPI program, DatabaseAPI database, LoggerAPI logger) {
    this.transaction = transaction
    this.program = program
    this.database = database
    this.logger = logger
  }

  public void main() {
    CONO = program.LDAZD.CONO as int

    String FACI = transaction.parameters.get("FACI")
    if (!FACI) {
      logger.debug("Facility not found")
      return
    }

    String RIDN = transaction.parameters.get("MFNO")
    if (!RIDN) {
      logger.debug("Order number not found")
      return
    }

    int RIDL = transaction.parameters.get("MSEQ") as int
    if (!RIDL) {
      logger.debug("Material sequence not found")
      return
    }
    String BANO = transaction.parameters.get("BANO")
    if (!BANO) {
      logger.debug("Lot number not found")
      return
    }

    String CAMU = transaction.parameters.get("CAMU")
    if (!CAMU) {
      logger.debug("Container number not found")
      return
    }

    String PRNO = getProduct(FACI, RIDN)
    if (!PRNO) {
      logger.debug("Product number for order ${RIDN} not found")
      return
    }

    String ITNO = getMaterialItem(FACI, PRNO, RIDN, RIDL)
    if (!ITNO) {
      logger.debug("Material item for order ${RIDN}, seq ${RIDL} not found")
      return
    }


    String foundCAMU = findLastIssuedContainer(ITNO, BANO, RIDN, RIDL, CAMU)

    if (!foundCAMU) {
      logger.debug("Could not find the most recent issued container for this ITNO:${ITNO}, BANO:${BANO}, MFNO:${RIDN}, MSEQ:${RIDL} with base container ${CAMU}")
    } else {
      logger.debug("Found container ${foundCAMU}")
      transaction.parameters.put("CAMU", foundCAMU)
    }


  }

  /**
   * Gets the product number based on facility and order number
   * @param FACI
   * @param MFNO
   * @return
   */
  private String getProduct(String FACI, String MFNO) {
    String product = null

    DBAction actionMWOHED = database.table("MWOHED").index("55").selection("VHPRNO").build()
    DBContainer containerMWOEHD = actionMWOHED.createContainer()
    containerMWOEHD.setInt("VHCONO", CONO)
    containerMWOEHD.setString("VHFACI", FACI)
    containerMWOEHD.setString("VHMFNO", MFNO)
    int keys = 3
    int limit = 1
    actionMWOHED.readAll(containerMWOEHD, keys, limit, { DBContainer c ->
      product = c.get("VHPRNO").toString()
    })

    return product
  }


  /**
   * Gets the material item number
   * @param FACI
   * @param PRNO
   * @param MFNO
   * @param MSEQ
   * @return
   */
  private String getMaterialItem(String FACI, String PRNO, String MFNO, int MSEQ) {
    String materialitem = null
    DBAction actionMWOMAT = database.table("MWOMAT").index("00").selection("VMMTNO").build()
    DBContainer containerMWOMAT = actionMWOMAT.createContainer()
    containerMWOMAT.setInt("VMCONO", CONO)
    containerMWOMAT.setString("VMFACI", FACI)
    containerMWOMAT.setString("VMPRNO", PRNO)
    containerMWOMAT.setString("VMMFNO", MFNO)
    containerMWOMAT.setInt("VMMSEQ", MSEQ)

    if (actionMWOMAT.read(containerMWOMAT)) {
      materialitem = containerMWOMAT.get("VMMTNO").toString()
    }
    return materialitem

  }

  /**
   * Finds the last issued container
   * @param ITNO
   * @param BANO
   * @param RIDN
   * @param RIDL
   * @param CAMU
   * @return
   */
  private String findLastIssuedContainer(String ITNO, String BANO, String RIDN, int RIDL, String CAMU) {
    String foundCAMU = null

    ExpressionFactory exp = database.getExpressionFactory("MITTRA")
    exp = exp.like("MTCAMU", CAMU + "%")

    DBAction actionMITTRA = database.table("MITTRA")
      .index("20")
      .matching(exp)
      .reverse()
      .selection("MTTRQT", "MTCAMU")
      .build()

    DBContainer containerMITTRA = actionMITTRA.createContainer()
    containerMITTRA.setInt("MTCONO", CONO)
    containerMITTRA.setString("MTITNO", ITNO)
    containerMITTRA.setString("MTBANO", BANO)
    containerMITTRA.setInt("MTTTYP", 11)  // material issue
    containerMITTRA.setString("MTRIDN", RIDN)
    containerMITTRA.setInt("MTRIDL", RIDL)

    int keys = 6
    actionMITTRA.readAll(containerMITTRA, keys, { DBContainer c ->
      double TRQT = c.getDouble("MTTRQT")
      if (!foundCAMU && TRQT < 0) {
        foundCAMU = c.get("MTCAMU").toString()
      }
    })

    return foundCAMU
  }
}
