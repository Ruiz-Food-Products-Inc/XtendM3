/**
 * README
 * This transaction will get all discount related details for an order line
 *
 * Name: EXT100MI_GetDiscountDet
 * Description: Get discount details
 * Date	      Changed By            Description
 * 20230217	  JHAGLER               initial development
 */
public class GetDiscountDet extends ExtendM3Transaction {
  private final MIAPI mi
  private final DatabaseAPI database
  private final ProgramAPI program
  private final LoggerAPI logger
  private final MICallerAPI miCaller

  public GetDiscountDet(MIAPI mi, DatabaseAPI database, ProgramAPI program, LoggerAPI logger, MICallerAPI miCaller) {
    this.mi = mi
    this.database = database
    this.program = program
    this.logger = logger
    this.miCaller = miCaller
  }


  public void main() {
    int CONO = program.LDAZD.CONO as int
    String ORNO = mi.inData.get("ORNO").toString()
    int PONR = mi.inData.get("PONR") as int

    Map<String, String> orderHeader = getOrderHeader(CONO, ORNO)
    Map<String, String> orderLine = getOrderLine(CONO, ORNO, PONR)
    Map<String, String> item = getItem(CONO, orderLine.get("&ITNO"))
    Map<String, String> customer = getCustomer(CONO, orderHeader.get("&CUNO"))
    Map<String, String> data = orderHeader + orderLine + item + customer as LinkedHashMap<String, String>

    logger.debug("Order line data: ${data}".toString())

    String DISY = orderHeader.get("DISY")
    int ORDT = orderHeader.get("ORDT") as int  // customer order date


    // loop for all 6 discount numbers
    for (int DIPO = 1; DIPO <= 6; DIPO++) {
      LinkedHashMap<String, ?> details = getDiscountDetails(CONO, DISY, DIPO, ORDT, data) as LinkedHashMap<String, ?>
      logger.debug("Discount details: ${details}".toString())
      if (details) {

        int FVDT = details.get("FVDT") as int
        int PREX = details.get("PREX") as int
        String OBV1 = details.get("OBV1")
        String OBV2 = details.get("OBV2")
        String OBV3 = details.get("OBV3")
        String OBV4 = details.get("OBV4")
        String OBV5 = details.get("OBV5")

        mi.outData.put("ORNO", ORNO)
        mi.outData.put("PONR", PONR.toString())
        mi.outData.put("DISY", DISY)
        mi.outData.put("DIPO", DIPO.toString())
        mi.outData.put("FVDT", FVDT.toString())
        mi.outData.put("PREX", PREX.toString())
        mi.outData.put("OBV1", OBV1.toString())
        mi.outData.put("OBV2", OBV2.toString())
        mi.outData.put("OBV3", OBV3.toString())
        mi.outData.put("OBV4", OBV4.toString())
        mi.outData.put("OBV5", OBV5.toString())

        String scaleBase = details.get("SGGB").toString()
        mi.outData.put("SGGB", scaleBase)
        mi.outData.put("TX15", details.get("TX15").toString())

        double scaleAmount = 0
        if (scaleBase == '62') {
          double orderGrossWeight = orderHeader.get("GRWE") as double
          if (orderGrossWeight) {
            scaleAmount = orderGrossWeight
          }
        }
        if (scaleBase == '22') {
          double lineOrderQty = orderLine.get("ORQA") as double
          if (lineOrderQty) {
            scaleAmount = lineOrderQty
          }
        }
        mi.outData.put("SBQT", scaleAmount.toString())

        Map<String, Double> scale = getDiscountScaleLineAmount(DISY, DIPO, FVDT, PREX, OBV1, OBV2, OBV3, OBV4, OBV5, scaleAmount)
        mi.outData.put("LIMT", scale.get("LIMT").toString())
        mi.outData.put("DIAM", scale.get("DIAM").toString())

        if (details.get("A030")) {
          mi.outData.put("A030", details.get("A030").toString())
          mi.outData.put("A130", details.get("A130").toString())
          mi.outData.put("A230", details.get("A230").toString())
          mi.outData.put("A330", details.get("A330").toString())
          mi.outData.put("A430", details.get("A430").toString())
          mi.outData.put("A530", details.get("A530").toString())
          mi.outData.put("DAT1", details.get("DAT1").toString())
        }

        mi.write()
      }



    }
  }

  /**
   * Gets order header details
   * @param CONO
   * @param ORNO
   * @return details with OAWCON, &CUNO, DISY, ORDT
   */
  Map<String, String> getOrderHeader(int CONO, String ORNO) {
    Map<String, String> orderHeader = new HashMap<String, String>()

    DBAction actionOOHEAD = database.table("OOHEAD").index("00").selection("OAWCON", "OACUNO", "OADISY", "OAORDT", "OAGRWE").build()
    DBContainer containerOOHEAD = actionOOHEAD.createContainer()
    containerOOHEAD.set("OACONO", CONO)
    containerOOHEAD.set("OAORNO", ORNO)

    if (actionOOHEAD.read(containerOOHEAD)) {
      orderHeader.put("OAWCON", containerOOHEAD.get("OAWCON").toString().trim())
      orderHeader.put("&CUNO", containerOOHEAD.get("OACUNO").toString().trim())
      orderHeader.put("DISY", containerOOHEAD.get("OADISY").toString().trim())
      orderHeader.put("ORDT", containerOOHEAD.get("OAORDT").toString().trim())
      orderHeader.put("GRWE", containerOOHEAD.get("OAGRWE").toString().trim())
    } else {
      mi.error("Order ${ORNO} not found".toString())
      return
    }

    return orderHeader
  }


  /**
   * Gets order line details
   * @param CONO
   * @param ORNO
   * @param PONR
   * @return details with &ITNO, &PRRF, &MODL, &DWDT
   */
  Map<String, String> getOrderLine(int CONO, String ORNO, int PONR) {
    Map<String, String> orderHeader = new HashMap<String, String>()

    DBAction action = database.table("OOLINE").index("00").selection("OBDWDT", "OBITNO", "OBPRRF", "OBMODL", "OBORQA").build()
    DBContainer container = action.createContainer()
    container.set("OBCONO", CONO)
    container.set("OBORNO", ORNO)
    container.set("OBPONR", PONR as int)

    if (action.read(container)) {
      orderHeader.put("&ITNO", container.get("OBITNO").toString().trim())
      orderHeader.put("&PRRF", container.get("OBPRRF").toString().trim())
      orderHeader.put("&MODL", container.get("OBMODL").toString().trim())
      orderHeader.put("DWDT", container.get("OBDWDT").toString().trim())
      orderHeader.put("ORQA", container.get("OBORQA").toString().trim())
    } else {
      mi.error("Order line ${PONR} not found".toString())
      return
    }

    return orderHeader
  }


  /**
   * Gets item details
   * @param CONO
   * @param ITNO
   * @return details with MMITCL
   */
  Map<String, String> getItem(int CONO, String ITNO) {
    Map<String, String> item = new HashMap<String, String>()

    DBAction action = database.table("MITMAS").index("00").selection("MMITCL").build()
    DBContainer container = action.createContainer()
    container.set("MMCONO", CONO)
    container.set("MMITNO", ITNO)

    if (action.read(container)) {
      item.put("MMITCL", container.get("MMITCL").toString().trim())
    } else {
      mi.error("Item ${ITNO} not found".toString())
      return
    }

    return item
  }

  /**
   * Gets customer details
   * @param CONO
   * @param CUNO
   * @return details with OKCFC1, OKCFC3
   */
  Map<String, String> getCustomer(int CONO, String CUNO) {
    Map<String, String> customer = new HashMap<String, String>()

    DBAction action = database.table("OCUSMA").index("00").selection("OKCFC1", "OKCFC3").build()
    DBContainer container = action.createContainer()
    container.set("OKCONO", CONO)
    container.set("OKCUNO", CUNO)

    if (action.read(container)) {
      customer.put("OKCFC1", container.get("OKCFC1").toString().trim())
      customer.put("OKCFC3", container.get("OKCFC3").toString().trim())
    } else {
      mi.error("Customer ${CUNO} not found".toString())
      return
    }

    return customer
  }

  /**
   * Gets the most recent valid from date for a discount model and number
   * @param CONO
   * @param DISY
   * @param DIPO
   * @param DATE
   * @return
   */
  int getValidFromDate(int CONO, String DISY, int DIPO, int DATE) {

    ExpressionFactory exp = database.getExpressionFactory("OGDMTX")
    exp = exp.le("DXFVDT", DATE.toString())
    DBAction action = database.table("OGDMTX").index("00").matching(exp).selection("DXFVDT").reverse().build()
    DBContainer container = action.createContainer()
    container.set("DXCONO", CONO)
    container.set("DXDISY", DISY)
    container.set("DXDIPO", DIPO)

    int FVDT = 0
    int keys = 3
    int limit = 1
    action.readAll(container, keys, limit, { DBContainer c ->
      if (FVDT == 0) {  // get the first returned value
        FVDT = c.get("DXFVDT") as int
      }
    })
    return FVDT
  }

  /**
   * Gets the discount model configuration
   * @param CONO
   * @param DISY
   * @param DIPO
   * @return
   */
  LinkedHashMap<Integer, LinkedHashMap<String, String>> getDiscountConfig(int CONO, String DISY, int DIPO) {
    LinkedHashMap<Integer, LinkedHashMap<String, String>> config = new LinkedHashMap<Integer, LinkedHashMap<String, String>>()
    DBAction action = database.table("OGDIPO").index("00").selectAllFields().build()
    DBContainer container = action.createContainer()
    container.set("DTCONO", CONO)
    container.set("DTDISY", DISY)
    container.set("DTDIPO", DIPO)

    if (action.read(container)) {
      for (int PREX = 1; PREX <= 9; PREX++) {
        LinkedHashMap<String, String> ctrlValues = getControlFields(container, PREX)
        // add discount scale base

        if (ctrlValues) {
          ctrlValues.put("SGGB", container.get("DTSGGB").toString())
          ctrlValues.put("TX15", container.get("DTTX15").toString())
          config.put(PREX, ctrlValues)
        }
      }
    } else {
      // ignore if no config found
    }

    return config
  }

  /**
   * Gets discount model control fields
   * @param container
   * @param PREX
   * @return
   */
  LinkedHashMap<String, String> getControlFields(DBContainer container, int PREX) {
    String OBV1 = container.getString("DTPC${PREX}1".toString())
    String OBV2 = container.getString("DTPC${PREX}2".toString())
    String OBV3 = container.getString("DTPC${PREX}3".toString())
    String OBV4 = container.getString("DTPC${PREX}4".toString())
    String OBV5 = container.getString("DTPC${PREX}5".toString())

    if (OBV1.isEmpty() && OBV2.isEmpty() && OBV3.isEmpty() && OBV4.isEmpty() && OBV5.isEmpty()) {
      // priority is not configured
      return null
    } else {
      return [
        "OBV1": OBV1,
        "OBV2": OBV2,
        "OBV3": OBV3,
        "OBV4": OBV4,
        "OBV5": OBV5
      ]
    }
  }


  /**
   * Matches up the correct object values
   * @param data
   * @param controlFields
   * @return
   */
  LinkedHashMap<String, String> lookupDataValues(LinkedHashMap<String, String> data, LinkedHashMap<String, String> controlFields) {
    LinkedHashMap<String, String> values = new LinkedHashMap<String, String>()
    String field1 = controlFields.get("OBV1")
    String value1 = data.get(field1)
    if (field1 != null && !field1.isEmpty()) {
      values.put("OBV1", value1)
    }

    String field2 = controlFields.get("OBV2")
    String value2 = data.get(field2)
    if (field2 != null && !field2.isEmpty()) {
      values.put("OBV2", value2)
    }

    String field3 = controlFields.get("OBV3")
    String value3 = data.get(field3)
    if (field3 != null && !field3.isEmpty()) {
      values.put("OBV3", value3)
    }

    String field4 = controlFields.get("OBV4")
    String value4 = data.get(field4)
    if (field4 != null && !field4.isEmpty()) {
      values.put("OBV4", value4)
    }

    String field5 = controlFields.get("OBV5")
    String value5 = data.get(field5)
    if (field5 != null && !field5.isEmpty()) {
      values.put("OBV5", value5)
    }
    return values
  }

  /**
   * Finds the exact discount line associated for this order line
   * @param CONO
   * @param DISY
   * @param DIPO
   * @param FVDT
   * @param PREX
   * @param values
   * @return
   */
  LinkedHashMap<String, String> findDiscountLine(int CONO, String DISY, int DIPO, int FVDT, int PREX, LinkedHashMap<String, String> values) {
    DBAction action = database.table("OGDMTX").index("00").selectAllFields().build()
    DBContainer container = action.createContainer()
    container.set("DXCONO", CONO)
    container.set("DXDISY", DISY)
    container.set("DXDIPO", DIPO)
    container.set("DXFVDT", FVDT)
    container.set("DXPREX", " " + PREX)  // priority has a leading space

    String control1 = ""
    String control2 = ""
    String control3 = ""
    String control4 = ""
    String control5 = ""
    boolean matched = false

    LinkedHashMap<String, String> controlValues = null

    action.readAll(container, 4, { DBContainer c ->

      control1 = c.getString("DXOBV1").trim()
      control2 = c.getString("DXOBV2").trim()
      control3 = c.getString("DXOBV3").trim()
      control4 = c.getString("DXOBV4").trim()
      control5 = c.getString("DXOBV5").trim()

      if (!matched &&
        (control1.isEmpty() || control1 == values.get("OBV1") ?: "") &&
        (control2.isEmpty() || control2 == values.get("OBV2") ?: "") &&
        (control3.isEmpty() || control3 == values.get("OBV3") ?: "") &&
        (control4.isEmpty() || control4 == values.get("OBV4") ?: "") &&
        (control5.isEmpty() || control5 == values.get("OBV5") ?: "")
      ) {
        controlValues = [
          "OBV1": control1,
          "OBV2": control2,
          "OBV3": control3,
          "OBV4": control4,
          "OBV5": control5
        ]
        matched = true
      }
    })
    return controlValues

  }

  /**
   * Retrieves extension fields for this order line and discount number
   * @param CONO
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
  LinkedHashMap<String, String> getExtensionFieldValues(int CONO, String DISY, int DIPO, int FVDT, int PREX,
                                                        String OBV1, String OBV2, String OBV3, String OBV4, String OBV5) {
    LinkedHashMap<String, String> extensionValues = null

    // manually combining keys per existing interfaces with MEI/BSA
    String PK01 = DISY.padRight(10, " ") + PREX.toString().padLeft(2, " ")

    DBAction action = database.table("CUGEX1").index("00")
      .selection("F1A030", "F1A130", "F1A230", "F1A330", "F1A430", "F1A530", "F1DAT1")
      .build()
    DBContainer container = action.createContainer()
    container.set("F1CONO", CONO)
    container.set("F1FILE", "OGDIPO")
    container.set("F1PK01", PK01)
    container.set("F1PK02", DIPO.toString())
    container.set("F1PK03", FVDT.toString())
    container.set("F1PK04", OBV1)
    container.set("F1PK05", OBV2)
    container.set("F1PK06", OBV3)
    container.set("F1PK07", OBV4)
    container.set("F1PK08", OBV5)

    if (action.read(container)) {
      extensionValues = [
        "A030": container.getString("F1A030"),
        "A130": container.getString("F1A130"),
        "A230": container.getString("F1A230"),
        "A330": container.getString("F1A330"),
        "A430": container.getString("F1A430"),
        "A530": container.getString("F1A530"),
        "DAT1": container.getInt("F1DAT1").toString(),

      ]
    }

    return extensionValues

  }

  /**
   * Gets discount details
   * @param CONO
   * @param DISY
   * @param DIPO
   * @param DWDT
   * @param data
   * @return
   */
  public LinkedHashMap<String, ?> getDiscountDetails(int CONO, String DISY, int DIPO, int DWDT, LinkedHashMap<String, String> data) {

    LinkedHashMap<String, ?> details

    LinkedHashMap<Integer, LinkedHashMap<String, String>> config = getDiscountConfig(CONO, DISY, DIPO)

    logger.debug("discount config: ${config}")

    int FVDT = getValidFromDate(CONO, DISY, DIPO, DWDT)  // discount valid from date

    logger.debug("valid from date: ${FVDT}")

    for (int PREX in config.keySet()) {

      LinkedHashMap<String, String> controlFields = config.get(PREX)
      LinkedHashMap<String, String> dataValues = lookupDataValues(data, controlFields)
      LinkedHashMap<String, String> controlValues = findDiscountLine(CONO, DISY, DIPO, FVDT, PREX, dataValues)
      if (controlValues != null) {

        LinkedHashMap<String, String> extensionValues = getExtensionFieldValues(CONO, DISY, DIPO, FVDT, PREX,
          controlValues.get("OBV1"),
          controlValues.get("OBV2"),
          controlValues.get("OBV3"),
          controlValues.get("OBV4"),
          controlValues.get("OBV5")
        )
        details = new LinkedHashMap<String, ?>()
        details.put("DISY", DISY.padRight(10, " "))
        details.put("DIPO", DIPO.toString())
        details.put("FVDT", FVDT.toString())
        details.put("PREX", PREX.toString().padLeft(2, " "))
        details.put("OBV1", controlValues.get("OBV1"))
        details.put("OBV2", controlValues.get("OBV2"))
        details.put("OBV3", controlValues.get("OBV3"))
        details.put("OBV4", controlValues.get("OBV4"))
        details.put("OBV5", controlValues.get("OBV5"))
        details.put("SGGB", controlFields.get("SGGB"))
        details.put("TX15", controlFields.get("TX15"))

        if (extensionValues != null) {
          details.put("A030", extensionValues.get("A030"))
          details.put("A130", extensionValues.get("A130"))
          details.put("A230", extensionValues.get("A230"))
          details.put("A330", extensionValues.get("A330"))
          details.put("A430", extensionValues.get("A430"))
          details.put("A530", extensionValues.get("A530"))
          details.put("DAT1", extensionValues.get("DAT1"))
        }

        break
      }
    }

    return details
  }

  private Map<String, Double> getDiscountScaleLineAmount(String DISY, int DIPO, int FVDT, int PREX,
                       String OBV1, String OBV2, String OBV3, String OBV4, String OBV5, double LIMT) {
    double amount = 0
    double matchedLimit = 0
    def params = [
      "DISY": DISY,
      "DIPO": DIPO.toString(),
      "FVDT": FVDT.toString(),
      "PREX": PREX.toString(),
      "OBV1": OBV1,
      "OBV2": OBV2,
      "OBV3": OBV3,
      "OBV4": OBV4,
      "OBV5": OBV5
    ]
    miCaller.call("OIS800MI", "LstScaleLine", params, {Map<String, ?> resp ->
      logger.debug("OIS800MI.LstScaleLine: ${resp}")
      if (!resp.error) {
        double scaleLimit = resp.get("LIMT") as double
        if (LIMT >= scaleLimit) {
          amount = resp.get("DIAM") as double
          matchedLimit = scaleLimit
        }
      }
    })

    return ["LIMT": matchedLimit, "DIAM": amount]
  }

}
