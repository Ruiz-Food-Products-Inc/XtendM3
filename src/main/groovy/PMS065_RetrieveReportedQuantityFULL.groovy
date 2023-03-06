/**
 * README
 * This extension will implement Ruiz Foods' custom logic when issuing backflushed materials
 * A configuration parameter is set in the extension table (CUGEX1) aligned with MITFAC
 * Field F1A130 holds the setting parameter
 * Only setting 0 = only use available is implemented in this extension.
 *
 * When a backflush transaction is being processed, if the selected balance id has less quantity available than the
 * quantity to report, only the available quantity will be reported.
 * If no available quantity is found, a ZERO report will occur.
 *
 * Name: PMS065.retrieveReportedQuantity
 * Description: Adjust backflushed quantity
 * Date	      Changed By            Description
 * 20230207	  JHAGLER               initial development
 * 20230302   JHAGLER               check quantity in location
 */

class PMS065_RetrieveReportedQuantityFULL extends ExtendM3Trigger {

  private final MethodAPI method
  private final DatabaseAPI database
  private final MICallerAPI miCaller
  private final LoggerAPI logger

  private int CONO

  PMS065_RetrieveReportedQuantityFULL(MethodAPI method, DatabaseAPI database, MICallerAPI miCaller, LoggerAPI logger) {
    this.method = method
    this.database = database
    this.miCaller = miCaller
    this.logger = logger
  }

  void main() {
    boolean active = false

    // get method arguments
    double quantityToReport = method.getArgument(0) as double
    CONO = method.getArgument(1) as int
    String warehouse = method.getArgument(2).toString()
    String materialNumber = method.getArgument(3).toString()
    String issueFromLocation = method.getArgument(4).toString()

    // get additional fields
    String facility = getFacility(warehouse)
    String backflushSetting = getBackflushSetting(facility, materialNumber)

    if (backflushSetting == "0") { // 0 = only use available

      double availableQuantity = getAvailableQty(warehouse, materialNumber, issueFromLocation)

      if (availableQuantity && availableQuantity > 0) {

        logger.debug("quantityToReport:${quantityToReport}; availableQuantity:${availableQuantity}")
        if (quantityToReport > availableQuantity) {
          // issuing will result in a negative backflush
          // issue only up to the available quantity
          quantityToReport = availableQuantity
          active = true
          logger.debug("quantityToReport was greater than available quantity;  new quantityToReport is ${quantityToReport}")
        }

      } else {
        quantityToReport = 0
        active = true
        logger.debug("no quantity was available, report zero")
      }

    }

    // set return values
    Backflush backflush = new Backflush()
    backflush.setRPQA(quantityToReport)
    backflush.setBActive(active)
    method.setReturnValue(backflush)

  }

  /**
   * Get facility connected to the warehouse
   * @param warehouse
   * @return facility
   */
  String getFacility(String warehouse) {
    logger.debug("Retrieve warehouse for WHLO:${warehouse}")
    String facility = null
    def params = [
      "WHLO": warehouse
    ]
    miCaller.call("MMS005MI", "GetWarehouse", params, {Map<String, ?> resp ->
      facility = resp.get("FACI").toString()
    })
    logger.debug("Facility retrieved ${facility}")
    return facility
  }

  /**
   * Lookup extension table values in CUGEX1 for MITFAC
   * Field F1A130 hold the "Backflush Setting" code
   * 0 = Only use available
   * 1 = Allow negative
   * 2 = Prevent negative
   * Value may be blank
   *
   * @param facility
   * @param materialNumber
   * @return typeOfItem
   */
  String getBackflushSetting(String facility, String materialNumber) {
    logger.debug("Retrieve backflush setting for FACI=${facility}; ITNO=${materialNumber};")

    String backflushSetting = null

    def params = [
      "FILE": "MITFAC",
      "PK01": facility,
      "PK02": materialNumber
    ]
    miCaller.call("CUSEXTMI", "GetFieldValue", params, {Map<String, ?> resp ->
      backflushSetting = resp.get("A130").toString()
    })
    logger.debug("Backflush setting found '${backflushSetting}'")

    return backflushSetting.trim()
  }

  /**
   * This method will return the quantity available to report for the first (by FIFO) balance id
   * matching the company, warehouse, material number, and location.
   * Balance ids must be status 2-approved.
   * Balance id must have a positive on hand quantity.
   * @param warehouse
   * @param materialNumber
   * @param location
   * @return availableQty
   * */
  double getAvailableQty(String warehouse, String materialNumber, String location) {
    logger.debug("Retrieve quantity for  WHLO:${warehouse}; ITNO:${materialNumber}; WHSL:${location};".toString())
    double availableQty = 0
    ExpressionFactory exp = database.getExpressionFactory("MITLOC")
    exp = exp.eq("MLSTAS", "2") & exp.gt("MLSTQT", "0")  // balance id status must be 2-approved and have a positive on hand balance
    DBAction actionMITLOC = database.table("MITLOC").index("05")
      .selection("MLSTQT", "MLALQT", "MLPLQT").matching(exp).build()
    DBContainer containerMITLOC = actionMITLOC.createContainer()
    containerMITLOC.setInt("MLCONO", CONO)
    containerMITLOC.setString("MLWHLO", warehouse)
    containerMITLOC.setString("MLITNO", materialNumber)
    containerMITLOC.setString("MLWHSL", location)

    int keys = 4
    actionMITLOC.readAll(containerMITLOC, keys, { DBContainer c ->
      double onHand = c.getDouble("MLSTQT")
      double allocated = c.getDouble("MLALQT")
      double pickList = c.getDouble("MLPLQT")
      availableQty += onHand - allocated - pickList
      logger.debug("Quantity found STQT:${onHand}; ALQT:${allocated}; PLQT:${pickList}; availableQty:${availableQty};".toString())
    })

    logger.debug("Total quantity available is ${availableQty}")

    return availableQty

  }


}
