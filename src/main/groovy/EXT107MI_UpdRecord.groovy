/**
 * README
 * Updates the Status after Reclassification is performed/attempted
 *
 * Name: EXT107MI.UpdRecord
 * Description:  Updates the Status after Reclassification is performed/attempted
 * Date	      Changed By            Description
 * 20221203	  VIDVEN        Updates the Status after Reclassification is performed/attempted
 *
 */
import java.time.LocalDate
import java.time.format.DateTimeFormatter

public class UpdRecord extends ExtendM3Transaction {
  private final MIAPI mi
  private final DatabaseAPI database
  private final ProgramAPI program

  //Input fields
  private String iTRNR
  private String iPONR
  private String iBANO
  private String iCAMU
  private String iSTAT

  private int CONO

  public UpdRecord(MIAPI mi, DatabaseAPI database, ProgramAPI program) {
    this.mi = mi
    this.database = database
    this.program = program
  }

  /**
   * Main method
   */
  public void main() {
    iTRNR = mi.inData.get("TRNR") == null ? "" : mi.inData.get("TRNR").trim()
    iPONR = mi.inData.get("PONR") == null ? "0" : mi.inData.get("PONR").trim()
    iBANO = mi.inData.get("BANO") == null ? "" : mi.inData.get("BANO").trim()
    iCAMU = mi.inData.get("CAMU") == null ? "" : mi.inData.get("CAMU").trim()
    iSTAT = mi.inData.get("STAT") == null ? "" : mi.inData.get("STAT").trim()


    CONO = program.LDAZD.CONO.toString().toInteger()

    if (iPONR.trim() == '0') {
      mi.error("Line number can not be zero")
      return
    } else {
      DBAction actionEXT601 = database.table("EXT601").index("00").selection("EXCHNO").build()
      DBContainer containerEXT601 = actionEXT601.getContainer()

      containerEXT601.set("EXCONO", CONO)
      containerEXT601.set("EXTRNR", iTRNR.trim())
      containerEXT601.set("EXPONR", iPONR as int)
      containerEXT601.set("EXBANO", iBANO.trim())
      containerEXT601.set("EXCAMU", iCAMU.trim())

      /**
       * Closure for EXT601 Update
       */
      Closure<?> updateCallBack = { LockedResult lockedResult ->
        int changeNumber = lockedResult.getInt("EXCHNO")
        int newChangeNumber = changeNumber + 1

        lockedResult.set("EXSTAT", iSTAT)

        // Update changed information
        lockedResult.set("EXLMDT", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) as int)
        lockedResult.set("EXCHNO", newChangeNumber)
        lockedResult.set("EXCHID", program.getUser())
        lockedResult.update()
      }

      if (!actionEXT601.readLock(containerEXT601, updateCallBack)) {
        mi.error("Record does not exist")
        return
      }
    }
  }
}
