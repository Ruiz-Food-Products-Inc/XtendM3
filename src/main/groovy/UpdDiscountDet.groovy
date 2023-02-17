/**
 * README
 * This transaction will population customer extension values with discount details
 * associated to a customer order line.
 *
 * Name: EXT100MI_UpdDiscountDet
 * Description: Update discount details
 * Date	      Changed By            Description
 * 20230209	  JHAGLER               initial development
 * 20230217	  JHAGLER               use EXT100MI.GetDiscountDet
 */

class UpdDiscountDet extends ExtendM3Transaction {

  private final MIAPI mi
  private final ProgramAPI program
  private final LoggerAPI logger
  private final MICallerAPI miCaller

  public UpdDiscountDet(MIAPI mi, ProgramAPI program, LoggerAPI logger, MICallerAPI miCaller) {
    this.mi = mi
    this.program = program
    this.logger = logger
    this.miCaller = miCaller
  }

  public void main() {

    String ORNO = mi.inData.get("ORNO").toString()
    if (!ORNO) {
      mi.error("Order number is mandatory")
      return
    }

    int PONR = mi.inData.get("PONR") as int
    if (!PONR || PONR == 0) {
      mi.error("Line number is mandatory")
      return
    }

    logger.debug("Updating discount extension values for ORNO=${ORNO}; PONR=${PONR}")


    def details = getDiscountDetails(ORNO, PONR)
    if (!details) {
      mi.error("Could not retrieve details")
      return
    }

    for (def detail in details) {
      // check to see if this discount detail record has extension values
      if (detail.get("A030")) {
        manageExtensionValues(ORNO, PONR, detail)
      }
    }


  }


  /**
   * Retrieve discount details for order line using
   * EXT100MI.GetDiscountDet
   * @param ORNO
   * @param PONR
   * @return map of discount details
   */
  private List<Map<String, ?>> getDiscountDetails(String ORNO, int PONR) {
    List<Map<String, ?>> details = []

    def params = [
      "ORNO": ORNO,
      "PONR": PONR.toString()
    ]
    miCaller.call("EXT100MI", "GetDiscountDet", params, { Map<String, ?> resp ->
      if (resp.error) {
        details = null
      } else {
        logger.debug("Found discount details for PONR=${PONR} of ${resp}")
        details.add(resp)
      }
    })
    return details
  }

  /**
   * Adds or updates extension values
   * @param CONO
   * @param ORNO
   * @param PONR
   * @param detail
   * @return
   */
  public manageExtensionValues(String ORNO, int PONR, Map<String, ?> detail) {
    def keys = [
      "FILE": "OOLINE",
      "PK01": ORNO,
      "PK02": PONR.toString(),
      "PK03": detail.get("DIPO").toString(),
      "PK04": detail.get("OBV1").toString(),
      "PK05": detail.get("OBV2").toString(),
      "PK06": detail.get("OBV3").toString(),
      "PK07": detail.get("OBV4").toString(),
      "PK08": detail.get("OBV5").toString()
    ]

    def values = [
      "A030": detail.get("A030").toString(),
      "A130": detail.get("A130").toString(),
      "A230": detail.get("A230").toString(),
      "A330": detail.get("A330").toString(),
      "A430": detail.get("A430").toString(),
      "A530": detail.get("A530").toString(),
      "VEXI": "1"
    ]

    def valuesEx = [
      "DAT1": detail.get("DAT1").toString(),
      "VEXI": "1"
    ]

    logger.debug("CUSEXTMI Add/Chg FieldValues params: ${keys + values + valuesEx}")

    if (!addFieldValue(keys + values)) {
      chgFieldValue(keys + values)
    }
    chgFieldValueEx(keys + valuesEx)

  }


  /**
   * MI call to add extension values
   * @param params
   * @return
   */
  private boolean addFieldValue(Map<String, String> params) {
    boolean ok = false

    miCaller.call("CUSEXTMI", "AddFieldValue", params, { Map<String, ?> resp ->
      if (resp.error) {
        logger.debug("CUSEXTMI/AddFieldValue params: ${params}  resp: ${resp}")
      } else {
        ok = true
      }
    })
    return ok
  }


  /**
   * MI call to update extended fields
   * @param params
   * @return
   */
  private void chgFieldValueEx(Map<String, String> params) {
    miCaller.call("CUSEXTMI", "ChgFieldValueEx", params, { Map<String, ?> resp ->
      if (resp.error) {
        logger.debug("CUSEXTMI/AddFieldValue params: ${params}  resp: ${resp}")
      }
    })
  }


  /**
   * MI call to update existing record
   * @param params
   * @return
   */
  private void chgFieldValue(Map<String, String> params) {
    miCaller.call("CUSEXTMI", "ChgFieldValue", params, { Map<String, String> resp ->
      if (resp.error) {
        logger.debug("CUSEXTMI/UpdFieldValue params: ${params}  resp: ${resp}")
      }
    })
  }


}
