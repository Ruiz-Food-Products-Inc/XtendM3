/**
 * README
 * This batch program is to be executed after the initial data migration.
 *
 * Balance ids that share container numbers will be moved to new container numbers with suffixes
 * to ensure that containers are unique.
 *
 * Name: EXT001
 * Description: Container Migration
 * Date	      Changed By            Description
 * 20230207	  JHAGLER               initial development
 */
class EXT001 extends ExtendM3Batch {
  private final DatabaseAPI database
  private final BatchAPI batch
  private final MICallerAPI miCaller
  private final LoggerAPI logger
  private final ProgramAPI program

  EXT001(DatabaseAPI database, BatchAPI batch, MICallerAPI miCaller, LoggerAPI logger, ProgramAPI program) {
    this.database = database
    this.batch = batch
    this.miCaller = miCaller
    this.logger = logger
    this.program = program
  }

  void main() {

    logger.debug("Running batch program EXT001")

    int CONO = program.LDAZD.CONO as int

    // only look a records with a non blank BANO and CAMU
    ExpressionFactory exp = database.getExpressionFactory("MITLOC")
    exp = exp.ne("MLBANO", "").and(exp.ne("MLCAMU", ""))

    // index 99 is sorted by CONO, CAMU
    DBAction actionMITLOC = database.table("MITLOC").index("99").matching(exp).selection("MLSTQT", "MLALQT", "MLPLQT").build()
    DBContainer containerMITLOC = actionMITLOC.createContainer()
    containerMITLOC.setInt("MLCONO", CONO)

    String lastCAMU = ""
    int keys = 1
    actionMITLOC.readAll(containerMITLOC, keys, { DBContainer c ->
      String WHLO = c.getString("MLWHLO")
      String ITNO = c.getString("MLITNO")
      String WHSL = c.getString("MLWHSL")
      String BANO = c.getString("MLBANO")
      String CAMU = c.getString("MLCAMU")
      double ALQT = c.getDouble("MLALQT")
      double PLQT = c.getDouble("MLPLQT")

      if (CAMU == lastCAMU) {
        if (ALQT > 0 || PLQT > 0) {  // quantity must be available to move
          logger.debug("Container ${CAMU} has quantity allocated".toString())
        } else if (WHSL.contains("=>")) {  // balance id is in transitÒ
          logger.debug("Container ${CAMU} is in transit location".toString())
        } else {
          moveContainer(WHLO, ITNO, WHSL, BANO, CAMU)
        }
      }

      lastCAMU = CAMU

    })

  }


  /**
   * Call move container.  MHS850MI_AddMovePRE will assign new container number as needed.
   * @param WHLO
   * @param ITNO
   * @param WHSL
   * @param BANO
   * @param CAMU
   */
  void moveContainer(String WHLO, String ITNO, String WHSL, String BANO, String CAMU) {
    def params = [
      "PRFL": "*EXE",
      "E0PA": "WS",
      "E065": "WMS",
      "WHLO": WHLO,
      "ITNO": ITNO,
      "WHSL": WHSL,
      "BANO": BANO,
      "CAMU": CAMU,
      "TWSL": WHSL
    ]

    logger.debug("Calling MMS850MI/AddMove with ${params}".toString())
    // PRE trigger on MMS850MI/AddMove will generate a new container sequence
    miCaller.call("MMS850MI", "AddMove", params, { Map<String, ?> resp ->
      if (resp.error) {
        logger.error("Error calling MMS850MI/AddMove:  " + resp.errorMessage.toString())
      }
    })
  }


}
