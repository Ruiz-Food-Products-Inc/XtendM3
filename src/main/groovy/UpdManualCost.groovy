/**
 * README
 * This transaction will update the costing header file with a manual amount
 *
 * Name: EXT100MI_UpdDiscountDet
 * Description: Update discount details
 * Date	      Changed By            Description
 * 20230323	  JHAGLER               initial development
 */

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class UpdManualCost extends ExtendM3Transaction {

  private final MIAPI mi
  private final ProgramAPI program
  private final DatabaseAPI database

  private int CONO

  UpdManualCost(MIAPI mi, ProgramAPI program, DatabaseAPI database) {
    this.mi = mi
    this.program = program
    this.database = database
  }

  void main() {

    CONO = program.LDAZD.CONO as int

    String FACI = mi.inData.get("FACI").toString()
    if (FACI == null) {
      mi.error("Facility is mandatory.")
      return
    }

    String ITNO = mi.inData.get("ITNO").toString()
    if (ITNO == null) {
      mi.error("Item number is mandatory.")
      return
    }

    String STRT = mi.inData.get("STRT").toString()
    if (STRT == null) {
      mi.error("Structure type is mandatory.")
      return
    }

    String PCTP = mi.inData.get("PCTP").toString()
    if (PCTP == null) {
      mi.error("Cost type is mandatory.")
      return
    }

    String PCDT = mi.inData.get("PCDT").toString()
    if (PCDT == null) {
      mi.error("Cost date is mandatory.")
      return
    }
    if (!PCDT.isNumber()) {
      mi.error("Cost date is not valid.")
      return
    }

    // optional, but must be a valid number
    String CSU6 = mi.inData.get("CSU6").toString()
    if (CSU6 == null) {
      mi.error("Costing sum 6 is mandatory..")
      return
    }
    if (CSU6 != null && !CSU6.isNumber()) {
      mi.error("Costing sum 6 is not a valid number.")
      return
    }


    DBAction actionMCHEAD = database
      .table("MCHEAD")  // product costing header
      .index("20")  // CONO, FACI, ITNO, STRT, VASE (config id), PCTP, PCDT
      .selection("KOCHID", "KOCHNO", "KOLMDT", "KOLMTS")
      .build()
    DBContainer containerMCHEAD = actionMCHEAD.createContainer()
    containerMCHEAD.setInt("KOCONO", CONO)
    containerMCHEAD.setString("KOFACI", FACI)
    containerMCHEAD.setString("KOITNO", ITNO)
    containerMCHEAD.setString("KOSTRT", STRT)
    containerMCHEAD.setString("KOVASE", "")
    containerMCHEAD.setChar("KOPCTP", PCTP)
    containerMCHEAD.setInt("KOPCDT", PCDT as int)

    boolean read = actionMCHEAD.readLock(containerMCHEAD, { LockedResult r ->

      r.setDouble("KOASU6", CSU6 as double)
      r.setDouble("KOCSU6", CSU6 as double)
      r.setInt("KOMAUM", 1)

      r.setInt("KOLMDT", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) as int)
      r.setLong("KOLMTS", new Date().getTime())
      r.setString("KOCHID", program.getUser())
      r.setInt("KOCHNO", r.getInt("KOCHNO")++)
      r.update()

    })

    if (!read) {
      mi.error("Record does not exist.")
      return
    }
  }

}
