/**
 * README
 * This transaction will population update the discount amounts
 * using the values retrieved from EXT100MI.GetDiscountDet
 *
 * Name: EXT100MI_UpdDiscountDet
 * Description: Update discount details
 * Date	      Changed By            Description
 * 20230217	  JHAGLER               initial development
 */

public class UpdDiscountAmt extends ExtendM3Transaction {
  private final MIAPI mi
  private final ProgramAPI program
  private final MICallerAPI miCaller
  private final LoggerAPI logger

  private int CONO

  public UpdDiscountAmt(MIAPI mi, ProgramAPI program, MICallerAPI miCaller, LoggerAPI logger) {
    this.mi = mi
    this.program = program
    this.miCaller = miCaller
    this.logger = logger
  }

  public void main() {

    CONO = program.LDAZD.CONO as int
    String ORNO = mi.inData.get("ORNO").toString()
    if (!ORNO) {
      mi.error("Order number is mandatory")
      return
    }

    logger.debug("Updating discount amounts for order ${ORNO}")
    String PONR = mi.inData.get("PONR").toString().trim()

    List<String> orderLineNumbers = []
    if (PONR.isEmpty()) {
      logger.debug("Selecting all lines")
      def lineNumbers = getOrderLineNumbers(ORNO)
      if (lineNumbers) {
        orderLineNumbers = lineNumbers
      }
    } else {
      logger.debug("Only selecting line ${PONR}")
      orderLineNumbers.add(PONR)
    }

    if (orderLineNumbers.size() == 0) {
      mi.error("Could not retrieve orders lines")
      return
    }

    logger.debug("Found lines to process ${orderLineNumbers}")


    for (def lineNumber in orderLineNumbers) {

      logger.debug("Processing order line ${lineNumber}")
      Map<String, String> priceUpdateParams = [:]

      def line = getOrderLine(ORNO, lineNumber)
      def line2 = getOrderLine(ORNO, lineNumber)
      logger.debug("Order line details found ${line} ${line2}")

      def details = getDiscountDetails(ORNO, lineNumber)

      // loop over each discount number
      for (int DIPO = 1; DIPO <= 6; DIPO++) {
        logger.debug("Checking line ${PONR} discount ${DIPO}")
        double currentAmount = line2.get("DIA" + DIPO) as double
        double currentPercent = line2.get("DIA" + DIPO) as double
        double retrievedAmount = 0
        double retrievedPercent = 0

        for (def detail in details) {
          if (DIPO == detail.get("DIPO") as int) {
            logger.debug("Discount details found ${detail}")
            retrievedAmount = detail.get("DIAM") as double
            retrievedPercent = detail.get("DISP") as double
            break;
          }
        }

        logger.debug("Current amount=${currentAmount} and current percent=${currentPercent}")
        logger.debug("Retrieved amount=${retrievedAmount} and retrieved percent=${retrievedPercent}")

        if (currentAmount != retrievedAmount || currentPercent != retrievedPercent) {
          priceUpdateParams.put("DIA" + DIPO, retrievedAmount.toString())
          priceUpdateParams.put("DIP" + DIPO, retrievedPercent.toString())
          priceUpdateParams.put("DIC" + DIPO, "8")  // manually changed
        }

      }

      if (priceUpdateParams.keySet().size() > 0) {
        logger.debug("Updating discount amounts with ${priceUpdateParams}")
        updatePrice(ORNO, lineNumber, priceUpdateParams)
      }

    }
  }

  /**
   * Get order line numbers for the customer order
   * @param ORNO
   * @return
   */

  List<String> getOrderLineNumbers(String ORNO) {
    List<String> orderLineNumbers = []
    def params = [
      "ORNO": ORNO
    ]
    miCaller.call("OIS100MI", "LstLine", params, { Map<String, ?> resp ->
      int PONR = resp.get("PONR") as int
      orderLineNumbers.add(PONR.toString())
    })
    return orderLineNumbers
  }


  /**
   * Get the order line details using OIS100MI.GetLine2
   * @param ORNO
   * @param PONR
   * @return
   */
  Map<String, ?> getOrderLine(String ORNO, String PONR) {
    Map<String, ?> orderLine = null
    def params = [
      "ORNO": ORNO,
      "PONR": PONR,
    ]
    miCaller.call("OIS100MI", "GetLine", params, { Map<String, ?> resp ->
      orderLine = resp
    })
    return orderLine
  }

  /**
   * Get the order line details using OIS100MI.GetLine2
   * @param ORNO
   * @param PONR
   * @return
   */
  Map<String, ?> getOrderLine2(String ORNO, String PONR) {
    Map<String, ?> orderLine = null
    def params = [
      "ORNO": ORNO,
      "PONR": PONR,
    ]
    miCaller.call("OIS100MI", "GetLine2", params, { Map<String, ?> resp ->
      orderLine = resp
    })
    return orderLine
  }


  /**
   * Get the order line discount details from EXT100MI.GetDiscountDet
   * @param ORNO
   * @param PONR
   * @return
   */
  private List<Map<String, ?>> getDiscountDetails(String ORNO, String PONR) {
    List<Map<String, ?>> details = []
    def params = [
      "ORNO": ORNO,
      "PONR": PONR
    ]
    miCaller.call("EXT100MI", "GetDiscountDet", params, { Map<String, ?> resp ->
      details.add(resp)
    })
    return details
  }


  /**
   * Call OIS100MI.UpdPriceInfo to update the discount amounts
   * @param ORNO
   * @param PONR
   * @param params
   */
  private void updatePrice(String ORNO, String PONR, Map<String, String> params) {
    params.put("ORNO", ORNO)
    params.put("PONR", PONR)


    logger.debug("Calling OIS100MI/UpdPriceInfo with ${params}")
    miCaller.call("OIS100MI", "UpdPriceInfo", params, { Map<String, ?> resp ->
      if (resp.error) {
        logger.debug("error calling OIS100MI/UpdPriceInfo: ${resp.errorMessage}")
        mi.error(resp.errorMessage.toString())
        return
      }
    })
  }


}
