public class UpdDiscountAmt extends ExtendM3Transaction {
  private final MIAPI mi
  private final ProgramAPI program
  private final MICallerAPI miCaller
  private final LoggerAPI logger

  private int CONO

  public UpdDiscountAmt(MIAPI mi,ProgramAPI program, MICallerAPI miCaller,  LoggerAPI logger) {
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
    }

    logger.debug("Found lines to process ${orderLineNumbers}")


    for (def lineNumber in orderLineNumbers) {

      logger.debug("Processing order line ${lineNumber}")
      Map<String, String> priceUpdateParams = [:]

      def line = getOrderLine(ORNO, lineNumber)
      logger.debug("Order line details found ${line}")

      def details = getDiscountDetails(ORNO, lineNumber)

      for(def detail in details) {

        logger.debug("Discount details found ${detail}")
        // only modify discount scale base 62's - gross weight
        String scaleBase = detail.get("SGGB").toString()
        if (scaleBase != '62') {
          continue
        }

        int DIPO = detail.get("DIPO") as int
        double retrievedAmount = detail.get("DIAM") as double
        // for the call to OIS100MI.UpdPriceInfo, if setting value to zero, must pass a "?"
        String retrievedAmountStr = "?"
        if (retrievedAmount != 0) {
          retrievedAmountStr = retrievedAmount.toString()
        }

        if (DIPO == 1) {
          double currentAmount = line.get("DIA1") as double
          if (retrievedAmount != currentAmount) {
            priceUpdateParams.put("DIA1", retrievedAmountStr)
          }
        }

        if (DIPO == 2) {
          double currentAmount = line.get("DIA2") as double
          if (retrievedAmount != currentAmount) {
            priceUpdateParams.put("DIA2", retrievedAmountStr)
          }
        }

        if (DIPO == 3) {
          double currentAmount = line.get("DIA3") as double
          if (retrievedAmount != currentAmount) {
            priceUpdateParams.put("DIA3", retrievedAmountStr)
          }
        }

        if (DIPO == 4) {
          double currentAmount = line.get("DIA4") as double
          if (retrievedAmount != currentAmount) {
            priceUpdateParams.put("DIA4", retrievedAmountStr)
          }
        }

        if (DIPO == 5) {
          double currentAmount = line.get("DIA5") as double
          if (retrievedAmount != currentAmount) {
            priceUpdateParams.put("DIA5", retrievedAmountStr)
          }
        }

        if (DIPO == 6) {
          double currentAmount = line.get("DIA6") as double
          if (retrievedAmount != currentAmount) {
            priceUpdateParams.put("DIA6", retrievedAmountStr)
          }
        }
      }

      if (priceUpdateParams.keySet().size() > 0) {
        logger.debug("Updating discount amounts with ${priceUpdateParams}")
        updatePrice(ORNO, lineNumber, priceUpdateParams)
      }

    }
  }

  List<String> getOrderLineNumbers(String ORNO) {
    List<String> orderLineNumbers = []
    def params = [
      "ORNO": ORNO
    ]
    miCaller.call("OIS100MI", "LstLine", params, {Map<String, ?> resp ->
      int PONR = resp.get("PONR") as int
      orderLineNumbers.add(PONR.toString())
    })
    return orderLineNumbers
  }

  Map<String, ?> getOrderLine(String ORNO, String PONR) {
    Map<String, ?> orderLine = null
    def params = [
      "ORNO": ORNO,
      "PONR": PONR,
    ]
    miCaller.call("OIS100MI", "GetLine2", params, {Map<String, ?> resp ->
      orderLine = resp
    })
    return orderLine
  }


  private List<Map<String, ?>> getDiscountDetails(String ORNO, String PONR) {
    List<Map<String, ?>> details = []
    def params = [
      "ORNO": ORNO,
      "PONR": PONR
    ]
    miCaller.call("EXT100MI", "GetDiscountDet",  params, {Map<String, ?> resp ->
      details.add(resp)
    })
    return details
  }



  private void updatePrice(String ORNO, String PONR, Map<String, String> params) {
    params.put("ORNO", ORNO)
    params.put("PONR", PONR)

    miCaller.call("OIS100MI", "UpdPriceInfo", params, {Map<String, ?> resp ->
      if (resp.error) {
        mi.error(resp.error.toString())
      }
    })
  }




}
