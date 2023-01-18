/**
 * README
 * Add record to Parking lot for QMS Reclass on in transit
 *
 * Name: EXT107MI.AddRecord
 * Description: Add record to Parking lot for QMS Reclass on in transit
 * Date	      Changed By            Description
 * 20221203	  VIDVEN        Add record to Parking lot for QMS Reclass on in transit
 *
 */
import java.time.LocalDate
import java.time.format.DateTimeFormatter


public class AddRecord extends ExtendM3Transaction {
  private final MIAPI mi
  private final DatabaseAPI database
  private final ProgramAPI program

  //Input fields
  private String iWHLO
  private String iITNO
  private String iWHSL
  private String iBANO
  private String iCAMU
  private String iNSTS
  private String iSTAT
  private String getRIDN
  private String getRIDL
  private String getBANO
  private String getCAMU
  private String getITNO
  private double getALQT

  private int CONO

  public AddRecord(MIAPI mi, DatabaseAPI database, ProgramAPI program) {
    this.mi = mi
    this.database = database
    this.program = program
  }

  /**
   * Main method
   * @param
   * @return
   */
  public void main() {
    iWHLO = mi.in.get("WHLO") == null ? "" : mi.in.get("WHLO").toString().trim()
    iITNO = mi.in.get("ITNO") == null ? "" : mi.in.get("ITNO").toString().trim()
    iWHSL = mi.in.get("WHSL") == null ? "" : mi.in.get("WHSL").toString().trim()
    iBANO = mi.in.get("BANO") == null ? "" : mi.in.get("BANO").toString().trim()
    iCAMU = mi.in.get("CAMU") == null ? "" : mi.in.get("CAMU").toString().trim()
    iNSTS = mi.in.get("NSTS") == null ? "" : mi.in.get("NSTS").toString().trim()
    iSTAT = mi.in.get("STAT") == null ? "" : mi.in.get("STAT").toString().trim()

    CONO = program.LDAZD.CONO.toString() as int


    if (validateInputs(CONO, iWHLO, iITNO, iBANO, iCAMU, iWHSL)) {
      DBAction actionEXT601 = database.table("EXT601").index("00").selection("EXCHNO").build()
      DBContainer containerEXT601 = actionEXT601.getContainer()

      containerEXT601.set("EXCONO", CONO as int)
      containerEXT601.set("EXTRNR", getRIDN.trim())
      containerEXT601.set("EXPONR", getRIDL as int)
      containerEXT601.set("EXBANO", getBANO.trim())
      containerEXT601.set("EXCAMU", getCAMU.trim())

      /**
       * Closure for EXT601 Update
       */
      Closure<?> updateCallBack = { LockedResult lockedResult ->
        int changeNumber = lockedResult.get("EXCHNO").toString() as int
        int newChangeNumber = changeNumber + 1

        lockedResult.set("EXTRQT", getALQT)
        lockedResult.set("EXITNO", getITNO)
        lockedResult.set("EXNSTS", iNSTS as char)
        lockedResult.set("EXSTAT", iSTAT)

        // Update changed information
        lockedResult.set("EXLMDT", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) as int)
        lockedResult.set("EXCHNO", newChangeNumber)
        lockedResult.set("EXCHID", program.getUser())

        lockedResult.update()
      }

      if (!actionEXT601.readLock(containerEXT601, updateCallBack)) {
        containerEXT601.set("EXTRNR", getRIDN.trim())
        containerEXT601.set("EXPONR", getRIDL as int)
        containerEXT601.set("EXTRQT", getALQT)
        containerEXT601.set("EXITNO", getITNO.trim())
        containerEXT601.set("EXNSTS", iNSTS as char)
        containerEXT601.set("EXSTAT", iSTAT.trim())
        containerEXT601.set("EXCAMU", getCAMU.trim())
        containerEXT601.set("EXLMDT", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) as int)
        containerEXT601.set("EXCHNO", 0)
        containerEXT601.set("EXCHID", program.getUser())

        actionEXT601.insert(containerEXT601)
      } else {
        mi.error("Failed to insert record")
        return
      }
    }
  }

  /**
   * Check if inputs are valid
   * @param inCONO
   * @param inWHLO
   * @param inITNO
   * @param inBANO
   * @param inCAMU
   * @param inWHSL
   * @return true if inputs are valid
   */
  private boolean validateInputs(int inCONO, String inWHLO, String inITNO, String inBANO, String inCAMU, String inWHSL) {
    DBAction actionMITLOC = database.table("MITLOC").index("00").selection("MLCONO").build()
    DBContainer containerMITLOC = actionMITLOC.getContainer()

    containerMITLOC.set("MLCONO", inCONO)
    containerMITLOC.set("MLWHLO", inWHLO)
    containerMITLOC.set("MLITNO", inITNO)
    containerMITLOC.set("MLBANO", inBANO)
    containerMITLOC.set("MLCAMU", inCAMU)
    containerMITLOC.set("MLWHSL", inWHSL)

    if (!actionMITLOC.read(containerMITLOC)) {
      mi.error("Balance does not exist")
      return false
    } else {
      DBAction actionMITALO = database.table("MITALO").index("00").selection("MQRIDN", "MQRIDL", "MQBANO", "MQCAMU", "MQALQT", "MQITNO").build()
      DBContainer containerMITALO = actionMITALO.getContainer()

      containerMITALO.set("MQCONO", inCONO)
      containerMITALO.set("MQWHLO", inWHLO)
      containerMITALO.set("MQITNO", inITNO)
      containerMITALO.set("MQWHSL", inWHSL)
      containerMITALO.set("MQBANO", inBANO)
      containerMITALO.set("MQCAMU", inCAMU)

      /**
       * Closure for EXT601 Update
       */
      Closure<?> callBack = { DBContainer resultContainer ->
        getRIDN = resultContainer.get("MQRIDN").toString()
        getRIDL = resultContainer.get("MQRIDL").toString() as int
        getBANO = resultContainer.get("MQBANO").toString()
        getCAMU = resultContainer.get("MQCAMU").toString()
        getALQT = resultContainer.get("MQALQT").toString() as double
        getITNO = resultContainer.get("MQITNO").toString()
      }

      if (!actionMITALO.readAll(containerMITALO, 6, callBack)) {
        mi.error("Balance is not allocated to transfer")
        return false
      }
    }
    return true
  }
}
