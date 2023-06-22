/**
 * README
 * This transaction will attempt to merge balance ids that share the same:
 * Warehouse, Location, Lot, Base container, and Status
 *
 * Name: MergeContainer
 * Description: Merges like containers
 * Date	      Changed By            Description
 * 20230207	  JHAGLER               initial development
 * 20230406	  JHAGLER               only allow COMG 7
 * 20230621	  JHAGLER               bugfix array.size -> array.size()
 * 20230622	  JHAGLER               feedback from Infor team
 */

public class MergeContainer extends ExtendM3Transaction {
  private final MIAPI mi
  private final MICallerAPI miCaller
  private final DatabaseAPI database
  private final LoggerAPI logger

  public MergeContainer(MIAPI mi, DatabaseAPI database, MICallerAPI miCaller, LoggerAPI logger) {
    this.mi = mi
    this.database = database
    this.miCaller = miCaller
    this.logger = logger
  }

  public void main() {

    int iCONO = mi.in.get('CONO') as int
    String iWHLO = mi.in.get('WHLO')
    String iWHSL = mi.in.get('WHSL')
    String iITNO = mi.in.get('ITNO')
    String iSTAS = mi.in.get('STAS')
    String iBANO = mi.in.get('BANO')
    String iCAMU = mi.in.get('CAMU')


    String COMG = getContainerManagementCode(iWHLO, iITNO)
    if (COMG != "7") {
      // overriding the container number should only be done for container managed 7-Package
      return
    }


    String baseCAMU = iCAMU.split("_")[0]

    ExpressionFactory exp = database.getExpressionFactory("MITLOC")
    exp = exp.like("MLCAMU", baseCAMU + "%")
    DBAction actionMITLOC = database.table("MITLOC").index("10")
      .selection("MLCAMU").matching(exp).build()

    DBContainer containerMITLOC = actionMITLOC.getContainer()
    containerMITLOC.set("MLCONO", iCONO)
    containerMITLOC.set("MLITNO", iITNO)
    containerMITLOC.set("MLSTAS", iSTAS)
    containerMITLOC.set("MLBANO", iBANO)
    containerMITLOC.set("MLWHLO", iWHLO)
    containerMITLOC.set("MLWHSL", iWHSL)

    List<String> containers = []
    int keys = 6

    int records = actionMITLOC.readAll(containerMITLOC, keys,{ DBContainer container ->
      String CAMU = container.get("MLCAMU").toString()
      containers.add(CAMU)
    })

    if (records == 0) {
      mi.error("No balance ids were found.")
      return
    }

    logger.debug(containers.toString())

    // get the first container in the list
    // this will be the container that everything is merged actionMITLOC
    String firstContainer = containers[0]
    // loop over containers starting at the SECOND element
    for (int i=1; i<(containers.size() as int); i++) {
      def params = [
        "PRFL": "*EXE",
        "E0PA": "WS",
        "E065": "WMS",
        "WHLO": iWHLO,
        "WHSL": iWHSL,
        "ITNO": iITNO,
        "BANO": iBANO,
        "CAMU": containers[i],
        "TWSL": iWHSL,
        "TOCA": firstContainer
      ]
      miCaller.call("MMS850MI", "AddMove", params, {Map<String, ?> resp ->
        if (resp.error) {
          mi.error(resp.get("errorMessage").toString())
        }
      })
    }

    mi.outData.put("CAMU", firstContainer)
    mi.write()

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
