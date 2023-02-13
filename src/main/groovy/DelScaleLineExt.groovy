/**
 * README
 * Removes customer extension table data for discount scale line
 *
 * Name: EXT800MI_DelScaleLineExt
 * Description: Removes customer extension table data for discount scale line
 * Date	      Changed By            Description
 * 20230207	  JHAGLER               initial development
 */
public class DelScaleLineExt extends ExtendM3Transaction {
  private final MIAPI mi
  private final MICallerAPI miCaller
  private final ProgramAPI program
  private final LoggerAPI logger
  private final DatabaseAPI database

  public DelScaleLineExt(MIAPI mi, MICallerAPI miCaller, ProgramAPI program, LoggerAPI logger, DatabaseAPI database) {
    this.mi = mi
    this.miCaller = miCaller
    this.program = program
    this.logger = logger
    this.database = database
  }


  public void main() {

    logger.debug("Input values: ${mi.inData}".toString())

    // extension values in CUGEX1 (OGDIPO) do not include scale as part of the keys
    // only after ALL records for all scales are deleted, should the extesion value be deleted
    int count = getMatchingRecordsCount()
    logger.debug("Found ${count} matching records")
    if (count == 0) {
      logger.debug("Deleting extension field values")
      delFieldValue()
    }


  }

  /**
   * Gets the count of records in OGDMTX
   * There may be multiple scale limit set
   * @return
   */
  int getMatchingRecordsCount() {
    int CONO = program.LDAZD.CONO as int
    DBAction action = database.table("OGDMTX").index("00")
      .selection("DXOBV1", "DXOBV2", "DXOBV3", "DXOBV4", "DXOBV5").build()
    DBContainer container = action.createContainer()

    container.setInt("DXCONO", CONO)
    container.setString("DXDISY", mi.inData.get("DISY"))
    container.setInt("DXDIPO", mi.inData.get("DIPO") as int)
    container.setInt("DXFVDT", mi.inData.get("FVDT") as int)
    container.setString("DXPREX", mi.inData.get("PREX"))
    container.setString("DXOBV1", mi.inData.get("OBV1"))
    container.setString("DXOBV2", mi.inData.get("OBV2"))
    container.setString("DXOBV3", mi.inData.get("OBV3"))
    container.setString("DXOBV4", mi.inData.get("OBV4"))
    container.setString("DXOBV5", mi.inData.get("OBV5"))

    int keys = 10
    int count = action.readAll(container, keys, {})
    return count
  }

  /**
   * Deletes record from CUGEX1 with extension data
   */
  void delFieldValue() {
    String DISY = mi.inData.get("DISY").padRight(10, " ")
    String PREX = mi.inData.get("PREX").padLeft(2, " ")
    String PK01 = DISY + PREX

    def params = [
      "FILE": "OGDIPO",
      "PK01": PK01,
      "PK02": mi.inData.get("DIPO"),
      "PK03": mi.inData.get("FVDT"),
      "PK04": mi.inData.get("OBV1"),
      "PK05": mi.inData.get("OBV2"),
      "PK06": mi.inData.get("OBV3"),
      "PK07": mi.inData.get("OBV4"),
      "PK08": mi.inData.get("OBV5")
    ]

    logger.error("CUSEXTMI/DelFieldValue params: ${params}".toString())

    miCaller.call("CUSEXTMI", "DelFieldValue", params, { Map<String, ?> resp ->
      if (resp.error) {
        logger.error("CUSEXTMI/DelFieldValue error: ${resp}".toString())
        mi.error(resp.errorMessage.toString())
      }
    })
  }

}
