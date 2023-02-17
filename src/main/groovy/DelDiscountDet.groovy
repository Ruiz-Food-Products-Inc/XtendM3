/**
 * README
 * Deletes all customer extension field values with discount details populated
 * A single customer order line will have multiple discount details that need to be
 * removed upon deletion of the order line
 *
 * Name: EXT100MI_DelDiscountDet
 * Description: Delete discount details
 * Date	      Changed By            Description
 * 20230209	  JHAGLER               initial development
 */

class DelDiscountDet extends ExtendM3Transaction {

  private final MIAPI mi
  private final DatabaseAPI database
  private final ProgramAPI program
  private final LoggerAPI logger
  private final MICallerAPI miCaller

  public DelDiscountDet(MIAPI mi, DatabaseAPI database, ProgramAPI program, LoggerAPI logger, MICallerAPI miCaller) {
    this.mi = mi
    this.database = database
    this.program = program
    this.logger = logger
    this.miCaller = miCaller
  }

  public void main() {
    int CONO = program.getLDAZD().CONO as int
    String ORNO = mi.inData.get("ORNO").toString()
    int PONR = mi.inData.get("PONR") as int

    logger.debug("Deleting extension table values for CONO:${CONO}; ORNO:${ORNO}; PONR;${PONR}".toString())

    DBAction actionCUGEX1 = database.table("CUGEX1").index("00")
      .selection("F1FILE", "F1PK01","F1PK02","F1PK03","F1PK04","F1PK05","F1PK06","F1PK07","F1PK08").build()
    DBContainer containerCUGEX1 = actionCUGEX1.createContainer()
    containerCUGEX1.setInt("F1CONO", CONO)
    containerCUGEX1.setString("F1FILE", "OOLINE")
    containerCUGEX1.setString("F1PK01", ORNO)
    containerCUGEX1.setString("F1PK02", String.format("%05d", PONR)) // integer values are padded with 5 places

    int keys = 4
    actionCUGEX1.readAll(containerCUGEX1, keys, { DBContainer c ->
      def params = [
        "FILE": "OOLINE",
        "PK01": c.getString("F1PK01"),
        "PK02": c.getString("F1PK02"),
        "PK03": c.getString("F1PK03"),
        "PK04": c.getString("F1PK04"),
        "PK05": c.getString("F1PK05"),
        "PK06": c.getString("F1PK06"),
        "PK07": c.getString("F1PK07"),
        "PK08": c.getString("F1PK08")
      ]

      logger.debug("CUSEXTMI/DelFieldValue params: ${params}".toString())
      miCaller.call("CUSEXTMI", "DelFieldValue", params, {Map<String,?> resp ->
        if (resp.error) {
          logger.debug("CUSEXTMI/DelFieldValue error: ${resp}".toString())
        }
      })

    })

  }

}
