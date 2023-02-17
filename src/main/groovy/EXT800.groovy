import java.time.LocalDate
import java.time.format.DateTimeFormatter


/**
 * README
 * This batch program add discount scale line values to enable the "expiration" of a discount.
 *
 * When discounts are initially set up, extension table values are also added with an expiration date.
 *
 * This program adds a new "zero value" record for that scale line the day after the expiration date.
 * When a new line is added, lines that are still valid from the previous valid from date must be copied forward.
 *
 * Name: EXT800
 * Description: Manage expired discounts
 * Date	      Changed By            Description
 * 20230207	  JHAGLER               initial development
 */
class EXT800 extends ExtendM3Batch {

  private final DatabaseAPI database
  private final MICallerAPI miCaller
  private final LoggerAPI logger
  private final ProgramAPI program

  private final String EXT_FILE = "OGDIPO"

  private int CONO

    EXT800(DatabaseAPI database, MICallerAPI miCaller, LoggerAPI logger, ProgramAPI program) {
    this.database = database
    this.miCaller = miCaller
    this.logger = logger
    this.program = program
  }


  void main() {
    CONO = program.LDAZD.CONO as int

    List<String> DISYs = listDiscountModels()
    for (String DISY in DISYs) {
      List<String> DIPOs = ["1", "2", "3", "4", "5", "6"]
      for (String DIPO in DIPOs) {
        logger.debug("Processing scale lines for DISY=${DISY}; DIPO=${DIPO}".toString())
        processExpiredDiscountScaleLines(DISY, DIPO)
        LinkedHashSet<String> validFromDates = findValidFromDates(DISY, DIPO)
        processUnexpiredDiscountScaleLines(DISY, DIPO, validFromDates)
      }
    }

  }

  /**
   * List all of the discount models
   * @return
   */
  List<String> listDiscountModels() {
    List<String> models = []
    DBAction actionOGIDSY = database.table("OGDISY").index("00").build()
    DBContainer containerOGDISY = actionOGIDSY.createContainer()
    containerOGDISY.setInt("DBCONO", CONO)

    int keys = 1
    actionOGIDSY.readAll(containerOGDISY, keys, { DBContainer c -> models.add(c.getString("DBDISY"))
    })
    return models
  }

  /**
   * Manage the expiration of scale lines from a specific discount model and discount number
   * @param DISY
   * @param DIPO
   */
  void processExpiredDiscountScaleLines(String DISY, String DIPO) {

    ExpressionFactory exp = database.getExpressionFactory("OGDMTX")
    exp = exp.gt("DXDIAM", "0")

    DBAction actionOGDMTX = database.table("OGDMTX").index("00").matching(exp).build()
    DBContainer containerOGDMTX = actionOGDMTX.createContainer()
    containerOGDMTX.setInt("DXCONO", CONO)
    containerOGDMTX.setString("DXDISY", DISY)
    containerOGDMTX.setInt("DXDIPO", DIPO as int)

    int keys = 3
    actionOGDMTX.readAll(containerOGDMTX, keys, { DBContainer c ->
      String FVDT = c.getInt("DXFVDT").toString()
      String PREX = c.getString("DXPREX")
      String OBV1 = c.getString("DXOBV1")
      String OBV2 = c.getString("DXOBV2")
      String OBV3 = c.getString("DXOBV3")
      String OBV4 = c.getString("DXOBV4")
      String OBV5 = c.getString("DXOBV5")

      List<String> extValues = getExtensionValues(DISY, DIPO, FVDT, PREX, OBV1, OBV2, OBV3, OBV4, OBV5)
      if (extValues == null) {
        // no extension values retrieved
        logger.debug("No extension values found.")
        return
      }
      String DAT1 = extValues.get(6)



      String expDate = DAT1
      if (!expDate || expDate.isEmpty() || expDate == "0") {
        // no valid expiration date found for this scale line
        logger.debug("No expiration date found.  ${expDate}")
        return
      }
      String expDatePlus1 = addDay(expDate, 1)

      logger.debug("Scale line expiration date is ${expDate}")

      if (!scaleLineExists(DISY, DIPO, expDatePlus1, PREX, OBV1, OBV2, OBV3, OBV4, OBV5)) {
        logger.debug("No scale line found on ${expDatePlus1}, adding zero value scale line")
        double LIMT = 0
        double DIAM = 0
        double DISP = 0
        addScaleLine(DISY, DIPO, expDatePlus1, PREX, OBV1, OBV2, OBV3, OBV4, OBV5, LIMT, DIAM, DISP)
      }

    })

  }

  /**
   * Get the extension values for a specific scale line
   * @param DISY
   * @param DIPO
   * @param FVDT
   * @param PREX
   * @param OBV1
   * @param OBV2
   * @param OBV3
   * @param OBV4
   * @param OBV5
   * @return
   */
  List<String> getExtensionValues(String DISY, String DIPO, String FVDT, String PREX, String OBV1, String OBV2, String OBV3, String OBV4, String OBV5) {
    DBAction actionCUGEX1 = database.table("CUGEX1").index("00")
      .selection("F1A030", "F1A130", "F1A230", "F1A330", "F1A430", "F1A530", "F1DAT1")
      .build()
    DBContainer containerCUGEX1 = actionCUGEX1.createContainer()
    containerCUGEX1.setInt("F1CONO", CONO)
    containerCUGEX1.setString("F1FILE", EXT_FILE)
    containerCUGEX1.setString("F1PK01", DISY.padRight(10, " ") + PREX.padLeft(2, " "))
    containerCUGEX1.setString("F1PK02", DIPO)
    containerCUGEX1.setString("F1PK03", FVDT)
    containerCUGEX1.setString("F1PK04", OBV1)
    containerCUGEX1.setString("F1PK05", OBV2)
    containerCUGEX1.setString("F1PK06", OBV3)
    containerCUGEX1.setString("F1PK07", OBV4)
    containerCUGEX1.setString("F1PK08", OBV5)

    if (actionCUGEX1.read(containerCUGEX1)) {
      return [containerCUGEX1.getString("F1A030"),
              containerCUGEX1.getString("F1A130"),
              containerCUGEX1.getString("F1A230"),
              containerCUGEX1.getString("F1A330"),
              containerCUGEX1.getString("F1A430"),
              containerCUGEX1.getString("F1A530"),
              containerCUGEX1.getInt("F1DAT1").toString()]
    }
    return null
  }

  /**
   * Add a number of days to a date.  Dates are strings in YYYYMMDD format
   * @param date
   * @param daysToAdd
   * @return
   */
  String addDay(String date, int daysToAdd) {
    if (!date || date.isEmpty()) {
      return null
    } else {
      DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
      return LocalDate.parse(date, dtf).plusDays(daysToAdd).format(dtf)
    }
  }

  /**
   * Check to see if a specific scale line exists
   * @param DISY
   * @param DIPO
   * @param FVDT
   * @param PREX
   * @param OBV1
   * @param OBV2
   * @param OBV3
   * @param OBV4
   * @param OBV5
   * @return
   */
  boolean scaleLineExists(String DISY, String DIPO, String FVDT, String PREX, String OBV1, String OBV2, String OBV3, String OBV4, String OBV5) {
    DBAction actionOGDMTX = database.table("OGDMTX").index("00").build()
    DBContainer containerOGDMTX = actionOGDMTX.createContainer()
    containerOGDMTX.setInt("DXCONO", CONO)
    containerOGDMTX.setString("DXDISY", DISY)
    containerOGDMTX.setInt("DXDIPO", DIPO as int)
    containerOGDMTX.setInt("DXFVDT", FVDT as int)
    containerOGDMTX.setString("DXPREX", PREX)
    containerOGDMTX.setString("DXOBV1", OBV1)
    containerOGDMTX.setString("DXOBV2", OBV2)
    containerOGDMTX.setString("DXOBV3", OBV3)
    containerOGDMTX.setString("DXOBV4", OBV4)
    containerOGDMTX.setString("DXOBV5", OBV5)

    int keys = 10
    int records = actionOGDMTX.readAll(containerOGDMTX, keys, {DBContainer c -> })
    if (records > 0) {
      return true
    } else {
      return false
    }
  }

  /**
   * MI call to add a scale line
   * @param DISY
   * @param DIPO
   * @param FVDT
   * @param PREX
   * @param OBV1
   * @param OBV2
   * @param OBV3
   * @param OBV4
   * @param OBV5
   * @param LIMT
   * @param DIAM
   * @param DISP
   */
  private void addScaleLine(String DISY, String DIPO, String FVDT, String PREX, String OBV1, String OBV2, String OBV3, String OBV4, String OBV5, double LIMT, double DIAM, double DISP) {
    def params = [
      "DISY": DISY,
      "DIPO": DIPO,
      "FVDT": FVDT,
      "PREX": PREX,
      "OBV1": OBV1,
      "OBV2": OBV2,
      "OBV3": OBV3,
      "OBV4": OBV4,
      "OBV5": OBV5,
      "DITP": "2",
      "LIMT": LIMT.toString(),
      "DIAM": DIAM.toString(),
      "DISP": DISP.toString(),]

    logger.debug("Calling OIS800MI/AddScaleLine with ${params}".toString())

    miCaller.call("OIS800MI", "AddScaleLine", params, { Map<String, ?> resp ->
      if (resp.error) {
        logger.debug("AddScaleLine errored: " + resp)
      }
    })
  }

  /**
   * Process the still active "unexpired" discount lines for a specific discount model and number
   * @param DISY
   * @param DIPO
   * @param validFromDates
   */
  void processUnexpiredDiscountScaleLines(String DISY, String DIPO, LinkedHashSet<String> validFromDates) {

    DBAction actionOGDMTX = database.table("OGDMTX").index("00")
      .selection("DXLIMT", "DXDIAM", "DXDISP").build()
    DBContainer containerOGDMTX = actionOGDMTX.createContainer()
    containerOGDMTX.setInt("DXCONO", CONO)
    containerOGDMTX.setString("DXDISY", DISY)
    containerOGDMTX.setInt("DXDIPO", DIPO as int)

    int keys = 3
    actionOGDMTX.readAll(containerOGDMTX, keys, { DBContainer c ->
      String FVDT = c.getInt("DXFVDT").toString()
      String PREX = c.getString("DXPREX")
      String OBV1 = c.getString("DXOBV1")
      String OBV2 = c.getString("DXOBV2")
      String OBV3 = c.getString("DXOBV3")
      String OBV4 = c.getString("DXOBV4")
      String OBV5 = c.getString("DXOBV5")

      logger.debug("Processing FVDT=${FVDT}; PREX=${PREX}; OBV1=${OBV1}; OBV2=${OBV2}; OBV3=${OBV3}; OBV4=${OBV4}; OBV5=${OBV5}")

      List<String> extValues = getExtensionValues(DISY, DIPO, FVDT, PREX, OBV1, OBV2, OBV3, OBV4, OBV5)
      if (extValues == null) {
        logger.debug("No extension values found")
        return
      }
      String A030 = extValues.get(0)
      String A130 = extValues.get(1)
      String A230 = extValues.get(2)
      String A330 = extValues.get(3)
      String A430 = extValues.get(4)
      String A530 = extValues.get(5)
      String DAT1 = extValues.get(6)

      String expDate = DAT1
      if (!expDate || expDate.isEmpty() || expDate == "0") {
        logger.debug("No valid expiration date found ${expDate}".toString())
        return
      }

      for (String validFromDate in validFromDates.iterator()) {
        // filter for dates that should be valid for this scale line
        if (validFromDate >= FVDT && validFromDate <= expDate) {
          logger.debug("Valid from date ${validFromDate} is within range ${FVDT} to ${expDate}".toString())

          if (!scaleLineExists(DISY, DIPO, validFromDate, PREX, OBV1, OBV2, OBV3, OBV4, OBV5)) {
            double LIMT = c.getDouble("DXLIMT")
            double DIAM = c.getDouble("DXDIAM")
            double DISP = c.getDouble("DXDISP")
            addScaleLine(DISY, DIPO, validFromDate, PREX, OBV1, OBV2, OBV3, OBV4, OBV5, LIMT, DIAM, DISP)
            String PK01 = DISY.padRight(10, " ") + PREX.padLeft(2, " ")
            String PK02 = DIPO
            String PK03 = validFromDate
            String PK04 = OBV1
            String PK05 = OBV2
            String PK06 = OBV3
            String PK07 = OBV4
            String PK08 = OBV5

            addExtensionValue(EXT_FILE, PK01, PK02, PK03, PK04, PK05, PK06, PK07, PK08,
              A030, A130, A230, A330, A430, A530, DAT1)
          }
        }
      }

    })
  }

  /**
   * Add extension values for a scale line
   * @param FILE
   * @param PK01
   * @param PK02
   * @param PK03
   * @param PK04
   * @param PK05
   * @param PK06
   * @param PK07
   * @param PK08
   * @param A030
   * @param A130
   * @param A230
   * @param A330
   * @param A430
   * @param A530
   * @param DAT1
   */
  void addExtensionValue(String FILE, String PK01, String PK02, String PK03, String PK04, String PK05, String PK06, String PK07, String PK08, String A030, String A130, String A230, String A330, String A430, String A530, String DAT1) {
    def keys = ["FILE": FILE,
                "PK01": PK01,
                "PK02": PK02,
                "PK03": PK03,
                "PK04": PK04,
                "PK05": PK05,
                "PK06": PK06,
                "PK07": PK07,
                "PK08": PK08,
                "VEXI": '1' // do not validate keys
    ]
    def values = ["A030": A030,
                  "A130": A130,
                  "A230": A230,
                  "A330": A330,
                  "A430": A430,
                  "A530": A530]


    logger.debug("Calling CUSEXTMI/AddFieldValue: ${keys + values}".toString())
    miCaller.call("CUSEXTMI", "AddFieldValue", keys + values, { Map<String, ?> resp ->
      if (resp.error) {
        logger.debug("Error calling CUSEXTMI/AddFieldValue: ${resp}".toString())
      }
    })


    def valuesEx = ["DAT1": DAT1.toString()]

    logger.debug("Calling CUSEXTMI/ChgFieldValueEx: ${keys + valuesEx}".toString())
    miCaller.call("CUSEXTMI", "ChgFieldValueEx", keys + valuesEx, { Map<String, ?> resp ->
      if (resp.error) {
        logger.debug("Error calling CUSEXTMI/ChgFieldValueEx: ${resp}".toString())
      }
    })
  }


  /**
   * Retrieve the list of valid from dates for a specific discount model and discount number
   * @param DISY
   * @param DIPO
   * @return
   */
  LinkedHashSet<String> findValidFromDates(String DISY, String DIPO) {

    LinkedHashSet<String> validFromDates = new LinkedHashSet<String>()

    DBAction actionOGDMTX = database.table("OGDMTX").index("00").build()
    DBContainer containerOGDMTX = actionOGDMTX.createContainer()
    containerOGDMTX.setInt("DXCONO", CONO)
    containerOGDMTX.setString("DXDISY", DISY)
    containerOGDMTX.setInt("DXDIPO", DIPO as int)

    int keys = 3
    actionOGDMTX.readAll(containerOGDMTX, keys, { DBContainer c ->
      validFromDates.add(c.getInt("DXFVDT").toString())
    })

    return validFromDates

  }
}
