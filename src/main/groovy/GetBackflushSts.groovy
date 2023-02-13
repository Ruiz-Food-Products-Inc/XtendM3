/**
 * README
 * This transaction will evaluate what would happen should backflush transactions
 * be report.
 *
 * Name: EXT050MI.GetBackflushSts
 * Description: Get backflush status
 * Date	      Changed By            Description
 * 20230209	  JHAGLER               initial development
 */

class GetBackflushSts extends ExtendM3Transaction {
  private final MIAPI mi
  private final ProgramAPI program
  private final DatabaseAPI database
  private final LoggerAPI logger

    GetBackflushSts(MIAPI mi, ProgramAPI program, DatabaseAPI database, LoggerAPI logger) {
    this.mi = mi
    this.program = program
    this.database = database
    this.logger = logger
  }

  void main() {

    int CONO = program.LDAZD.CONO as int

    String FACI = mi.inData.get("FACI").toString()
    String MFNO = mi.inData.get("MFNO").toString()
    double RPQA = mi.inData.get("RPQA") as double

    String PRNO = getProduct(CONO, FACI, MFNO)

    String msg = validateBackflushedMaterials(CONO, FACI, PRNO, MFNO, RPQA)
    if (!msg.isEmpty()) {
      mi.outData.put("STAT", msg)
      mi.write()
    } else {
      mi.outData.put("STAT", "OK")
      mi.write()
    }

  }


  /**
   * Get the product number for the MO
   * @param CONO
   * @param FACI
   * @param MFNO
   * @return PRNO
   */
  String getProduct(int CONO, String FACI, String MFNO) {
    logger.debug("Getting product number for CONO:${CONO}; FACI:${FACI}; MFNO:${MFNO}".toString())

    String PRNO = null
    DBAction actionMWOHED = database.table("MWOHED").index("55").selection("VHPRNO").build()
    DBContainer containerMWOHED = actionMWOHED.createContainer()
    containerMWOHED.setInt("VHCONO", CONO)
    containerMWOHED.setString("VHFACI", FACI)
    containerMWOHED.setString("VHMFNO", MFNO)

    int keys = 3
    int limit = 1
    int records = actionMWOHED.readAll(containerMWOHED, keys, limit, { DBContainer c ->
      PRNO = c.getString("VHPRNO")
    })
    if (records == 0) {
      mi.error("Order ${MFNO} was not found.")
    }
    logger.debug("Getting product number is ${PRNO}".toString())
    return PRNO
  }


  /**
   * Validate the backflush status for a material
   * @param CONO
   * @param FACI
   * @param PRNO
   * @param MFNO
   * @param RPQA
   * @return message
   */
  String validateBackflushedMaterials(int CONO, String FACI, String PRNO, String MFNO, double RPQA) {
    String msg = ""

    DBAction actionMWOMAT = database.table("MWOMAT").index("00")
      .selection("VMMTNO", "VMWHLO", "VMSPMT", "VMCNQT", "VMWAPC", "VMWHSL").build()
    DBContainer containerMWOMAT = actionMWOMAT.createContainer()
    containerMWOMAT.setInt("VMCONO", CONO)
    containerMWOMAT.setString("VMFACI", FACI)
    containerMWOMAT.setString("VMPRNO", PRNO)
    containerMWOMAT.setString("VMMFNO", MFNO)

    int keys = 4
    actionMWOMAT.readAll(containerMWOMAT, keys, { DBContainer c ->
      String MTNO = c.getString("VMMTNO")
      String WHLO = c.getString("VMWHLO")
      int SPMT = c.getInt("VMSPMT")  // issue method

      logger.debug("Validating material ${MTNO} for backflush".toString())

      if (SPMT == 3 || SPMT == 5) {
        logger.debug("Material ${MTNO} is configured for backflush method ${SPMT}".toString())

        if (isPreventNegativeBackflush(CONO, FACI, MTNO)) {
          int DCCD = getNumberOfDecimals(CONO, MTNO)
          double CNQT = c.getDouble("VMCNQT")
          double WAPC = c.getDouble("VMWAPC")
          double toReport = calculateQtyToReport(CNQT, WAPC, RPQA, DCCD)

          // need to see if issue method 5 to use location on workcenter.....
          String WHSL = c.getString("VMWHSL")
          double available = getAvailableBackflushQty(CONO, WHLO, MTNO, WHSL)
          if (available < toReport) {
            logger.debug("Qty available (${available}) is less than qty to report (${toReport})")
            msg = "Backflush transaction for ${MTNO} will result in a negative on hand balance.".toString()
          }
        }
      }
    })

    return msg
  }


  /**
   * Get the number of decimals configured for an item
   * @param CONO
   * @param ITNO
   * @return nrOfDecimals
   */
  int getNumberOfDecimals(int CONO, String ITNO) {
    logger.debug("Getting number of decimals for CONO:${CONO}; ITNO:${ITNO};".toString())
    int DCCD = 0
    DBAction actionMITMAS = database.table("MITMAS").index("00").selection("MMDCCD").build()
    DBContainer containerMITMAS = actionMITMAS.createContainer()
    containerMITMAS.setInt("MMCONO", CONO)
    containerMITMAS.setString("MMITNO", ITNO)
    if (actionMITMAS.read(containerMITMAS)) {
      DCCD = containerMITMAS.getInt("MMDCCD")
    }
    logger.debug("Number of decimals is ${DCCD}".toString())
    return DCCD
  }

  /**
   * Check the configuration to see if this material should prevent negative backflushes
   * @param CONO
   * @param FACI
   * @param ITNO
   * @return
   */
  boolean isPreventNegativeBackflush(int CONO, String FACI, String ITNO) {
    logger.debug("Getting backflush setting for CONO:${CONO}; FACI:${FACI}; ITNO:${ITNO};".toString())
    String A130 = null
    DBAction actionCUGEX1 = database.table("CUGEX1").index("00").selection("F1A130").build()
    DBContainer containerCUGEX1 = actionCUGEX1.createContainer()
    containerCUGEX1.setInt("F1CONO", CONO)
    containerCUGEX1.setString("F1FILE", "MITFAC")
    containerCUGEX1.setString("F1PK01", FACI)
    containerCUGEX1.setString("F1PK02", ITNO)

    if (actionCUGEX1.read(containerCUGEX1)) {
      A130 = containerCUGEX1.getString("F1A130")
    }
    logger.debug("Backflush setting is ${A130}".toString())
    return A130 == "2"
  }


  /**
   * Calculate the quantity that would be backflushed
   * @param CNQT
   * @param WAPC
   * @param RPQA
   * @param DCCD
   * @return qtyToReport
   */
  double calculateQtyToReport(double CNQT, double WAPC, double RPQA, int DCCD) {
    logger.debug("Calculating quantity to report with CNQT:${CNQT}; WAPC:${WAPC}; RPQA:${RPQA}; DCCD:${DCCD};".toString())
    double qtyWithWaste = CNQT * (WAPC / 100 + 1)
    double toReport = RPQA * qtyWithWaste
    double toReportRounded = Math.ceil(toReport * (10**DCCD)) / (10**DCCD)
    logger.debug("Quantity to report is ${toReportRounded}".toString())
    return toReportRounded
  }


  /**
   * Get the available quantity to backflush from
   * @param CONO
   * @param WHLO
   * @param ITNO
   * @param WHSL
   * @return qty
   */
  double getAvailableBackflushQty(int CONO, String WHLO, String ITNO, String WHSL) {
    logger.debug("Getting available backflush quantity for CONO:${CONO}; WHLO:${WHLO}; ITNO:${ITNO}; WHSL:${WHSL};".toString())
    double available = 0
    ExpressionFactory exp = database.getExpressionFactory("MITLOC")
    exp = exp.eq("MLSTAS", "2") & exp.gt("MLSTQT", "0")  // balance ids must be status 2 - approved and positive

    DBAction actionMITLOC = database.table("MITLOC").index("05")  // use index 05 to order FIFO
      .matching(exp)
      .selection("MLSTQT", "MLALQT", "MLPLQT").build()

    DBContainer containerMITLOC = actionMITLOC.createContainer()
    containerMITLOC.setInt("MLCONO", CONO)
    containerMITLOC.setString("MLWHLO", WHLO)
    containerMITLOC.setString("MLITNO", ITNO)
    containerMITLOC.setString("MLWHSL", WHSL)

    int keys = 4
    int limit = 1
    actionMITLOC.readAll(containerMITLOC, keys, limit, { DBContainer c ->
      String BANO = c.getString("MLBANO")
      String CAMU = c.getString("MLCAMU")
      double STQT = c.getDouble("MLSTQT")
      double ALQT = c.getDouble("MLALQT")
      double PLQT = c.getDouble("MLPLQT")
      logger.debug("Selected balance id for backflush is WHLO:${WHLO}; ITNO:${ITNO}; WHSL:${WHSL}; BANO:${BANO}; CAMU:${CAMU}; STQT:${STQT}; ALQT:${ALQT}; PLQT:${PLQT};".toString())
      available = STQT - ALQT - PLQT
    })

    logger.debug("Available backflush quantity is ${available}".toString())
    return available
  }

}
