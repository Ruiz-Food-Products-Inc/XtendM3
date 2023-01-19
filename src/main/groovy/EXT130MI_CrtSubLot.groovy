/**
 * README
 * Create SubLot for the input Lot
 *
 * Name: EXT130MI.CrtSubLot
 * Description: Update a record in the OOHEAD table
 * Date	      Changed By            Description
 * 20221001	  VIDVEN        Create sub lot for an Existing Lot
 **/

public class CrtSubLot extends ExtendM3Transaction {
  private final MIAPI mi
  private final DatabaseAPI database
  private final ProgramAPI program
  private final MICallerAPI miCaller

  //Input fields
  private String iCONO
  private String iITNO
  private String iBANO

  public CrtSubLot(MIAPI mi, DatabaseAPI database, MICallerAPI miCaller, ProgramAPI program) {
    this.mi = mi
    this.database = database
    this.program = program
    this.miCaller = miCaller
  }

  /**
   * Main method
   * @param
   * @return
   */
  public void main() {

    iCONO = mi.inData.get("CONO") == null || mi.inData.get("CONO").trim() == "" || mi.inData.get("CONO").trim() == "?" ? program.LDAZD.CONO.toString() : mi.inData.get("CONO").trim()
    iITNO = mi.inData.get("ITNO") == null || mi.inData.get("ITNO").trim() == "?" ? "" : mi.inData.get("ITNO").trim()
    iBANO = mi.inData.get("BANO") == null || mi.inData.get("BANO").trim() == "?" ? "" : mi.inData.get("BANO").trim()

    boolean checkItem = validateItem(iCONO.toInteger(), iITNO)
    if (checkItem) {
      boolean checkItemLot = validateItemLot(iCONO.toInteger(), iITNO, iBANO)
      if (checkItemLot) {
        String newBANO = GetNextSubLotNumber(iCONO, iBANO, iITNO)
        createSubLot(iCONO.toInteger(), iITNO, newBANO)
      }
    }
  }

  /**
   * Validate item number
   * @param intCONO
   * @param inITNO
   * @return true if item number is valid
   */
  private boolean validateItem(int intCONO, String inITNO) {
    DBAction dbMITMAS = database.table("MITMAS").index("00").selection("MMCONO", "MMITNO").build()
    DBContainer MITMAS = dbMITMAS.getContainer()
    MITMAS.set("MMCONO", intCONO)
    MITMAS.set("MMITNO", inITNO)
    if (!dbMITMAS.read(MITMAS)) {
      mi.error("Item does not exist")
      return false
    }
    return true
  }

  /**
   *  Validate item/lot
   * @param inCONO
   * @param inITNO
   * @param inBANO
   * @return true if item/lot is valid
   */
  private boolean validateItemLot(int inCONO, String inITNO, String inBANO) {
    DBAction dbMILOMA = database.table("MILOMA").index("00").selectAllFields().build()
    DBContainer MILOMA = dbMILOMA.getContainer()
    MILOMA.set("LMCONO", inCONO)
    MILOMA.set("LMITNO", inITNO)
    MILOMA.set("LMBANO", inBANO.trim())
    if (!dbMILOMA.read(MILOMA)) {
      mi.error("Item/Lot record does not exist")
      return false
    }
    return true
  }

  /**
   * Get the next sublot number for the original lot
   * @param inCONO
   * @param inBANO
   * @param inITNO
   * @return sub-lot Number
   */
  public String GetNextSubLotNumber(String inCONO, String inBANO, String inITNO) {
    String OriginalBANO = ""

    if (inBANO.contains("|")) {
      int index = inBANO.lastIndexOf("|")
      OriginalBANO = inBANO.substring(0, index)
    } else {
      OriginalBANO = inBANO
    }
    DBAction actionMILOMA = database.table("MILOMA").index("00").selectAllFields().build()
    DBContainer containerMILOMA = actionMILOMA.getContainer()
    containerMILOMA.set("LMCONO", iCONO.toInteger())
    containerMILOMA.set("LMITNO", inITNO)
    int suffix = 0

    Closure<?> readAllRecordMILOMA = { DBContainer resultContainer ->
      String BANO = resultContainer.getString("LMBANO")
      if (BANO.contains("|")) {
        String Lot = BANO.substring(0, BANO.lastIndexOf("|"))
        String subLot = BANO.substring(BANO.lastIndexOf("|") + 1)

        if (Lot.trim() == OriginalBANO) {
          suffix = subLot.toInteger() > suffix ? subLot.toInteger() : suffix
        }
      }
    }
    if (!actionMILOMA.readAll(containerMILOMA, 2, readAllRecordMILOMA)) {
      mi.error("Item/Lot record does not exist")
    } else {
      //
    }
    return OriginalBANO + "|" + String.format("%03d", (suffix + 1))
  }

  /**
   * Create subLot - Calls MMS235MI.AddItmLot
   * @param inCONO
   * @param inITNO
   * @param inBANO
   * @return
   */
  private createSubLot(int inCONO, String inITNO, String inBANO) {
    Map<String, String> params = ["CONO": inCONO.toString().trim(), "ITNO": inITNO.toString().trim(), "BANO": inBANO.toString().trim()]
    Closure<?> handler = { Map<String,
      String> response ->
      if (response.error) {
        mi.error(response.errorMessage.toString())
        return
      } else {
        mi.outData.put("SBAN", inBANO)
        mi.write()
      }
    }
    miCaller.call("MMS235MI", "AddItmLot", params, handler)
  }
}
