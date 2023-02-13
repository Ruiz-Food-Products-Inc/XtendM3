/**
 * README
 * This trigger will retrieve the correct container number from a base container number
 * and pass that value along to MWSLWSMI.LstDOLine
 *
 * Name: LstDOLineContainer
 * Description: Search for correct container to report DO receipt
 * Date	      Changed By            Description
 * 20230209	  JHAGLER               initial development
 */
public class MWSLWMMI_LstDOLinePRE extends ExtendM3Trigger {

  private final TransactionAPI transaction
  private final ProgramAPI program
  private final DatabaseAPI database

  private int CONO

  public MWSLWMMI_LstDOLinePRE(TransactionAPI transaction, ProgramAPI program, DatabaseAPI database) {
    this.transaction = transaction
    this.program = program
    this.database = database
  }

  public void main() {

    CONO = program.LDAZD.CONO as int

    String WHLO = transaction.parameters.get("WHLO").toString()
    String CAMU = transaction.parameters.get("CAMU").toString()

    if (!CAMU.isEmpty()) {
      String baseCAMU = CAMU.trim().split("_")[0]
      CAMU = searchOpenDeliveries(WHLO, baseCAMU)
      transaction.parameters.put("CAMU", CAMU)
    }

  }

  /**
   * Search over open in bound deliveries for a matching container
   * @param WHLO
   * @param baseCAMU
   * @return selectedContainer
   */
  String searchOpenDeliveries(String WHLO, String baseCAMU) {
    String selectedContainer = null

    int INOU = 2
    String FRPG = "70"
    String TOPG = "89"

    // search open deliveries

    ExpressionFactory exp = database.getExpressionFactory("MHDISH")
    // warehouse
    exp = exp.eq("OQWHLO", WHLO)
    // delivery status
    exp = exp & exp.ge("OQPGRS", FRPG) & exp.le("OQPGRS", TOPG)

    DBAction actionMHDISH = database.table("MHDISH").index("00").matching(exp).selectAllFields().build()
    DBContainer containerMHDISH = actionMHDISH.createContainer()
    containerMHDISH.setInt("OQCONO", CONO)
    containerMHDISH.setInt("OQINOU", INOU)

    int keys = 2
    actionMHDISH.readAll(containerMHDISH, keys, { DBContainer MHDISH ->
      if (!selectedContainer) {
        long DLIX = MHDISH.getLong("OQDLIX")
        selectedContainer = searchInTransit(WHLO, DLIX, baseCAMU)
      }
    })

    return selectedContainer
  }

  /**
   * Search in-transit records for matching container for a delivery
   * @param WHLO
   * @param DLIX
   * @param baseCAMU
   * @return selectedContainer
   */
  String searchInTransit(String WHLO, long DLIX, String baseCAMU) {
    String selectedContainer = null

    ExpressionFactory exp = database.getExpressionFactory("MFTRNS")
    exp =  exp.like("OSCAMU", baseCAMU + "%")

    DBAction actionMFTRNS = database.table("MFTRNS")
      .index("00")
      .matching(exp)
      .selection("OSCAMU")
      .build()
    DBContainer containerMFTRNS = actionMFTRNS.createContainer()
    containerMFTRNS.setInt("OSCONO", CONO)
    containerMFTRNS.setString("OSWHLO", WHLO)
    containerMFTRNS.setLong("OSDLIX", DLIX)
    int keys = 3
    int limit = 1
    actionMFTRNS.readAll(containerMFTRNS, keys, limit, { DBContainer MFTRNS ->
      selectedContainer = MFTRNS.get("OSCAMU").toString()
    })

    return selectedContainer
  }
}
