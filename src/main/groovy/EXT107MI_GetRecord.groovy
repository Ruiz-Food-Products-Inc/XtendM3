/**
 * README
 * Gets Reclassification's scheduled for specific Balances part of given Delivery Line.
 *
 * Name: EXT107MI.GetRecord
 * Description: Gets Reclassification's scheduled for specific Balances part of given Delivery Line.
 * Date	      Changed By            Description
 * 20221203	  VIDVEN        Gets Reclassification's scheduled for specific Balances part of given Delivery Line.
 *
 */

public class GetRecord extends ExtendM3Transaction {
  private final MIAPI mi
  private final DatabaseAPI database
  private final ProgramAPI program

  //Input fields
  private String iTRNR
  private String iPONR
  private String iBANO
  private String iCAMU

  private int CONO;

  public GetRecord(MIAPI mi, DatabaseAPI database, ProgramAPI program) {
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
    iTRNR = mi.in.get("TRNR") == null ? "" : mi.inData.get("TRNR").toString().trim()
    iPONR = mi.in.get("PONR") == null ? "0" : mi.inData.get("PONR").toString().trim()
    iBANO = mi.in.get("BANO") == null ? "" : mi.inData.get("BANO").toString().trim()
    iCAMU = mi.in.get("CAMU") == null ? "" : mi.inData.get("CAMU").toString().trim()



    CONO = program.LDAZD.CONO.toString() as int
    if(iPONR.trim()=='0'){
      mi.error("Line number cannot be 0")
      return
    }
    else{
      /**
       * Validate and output record if record exists
       */
      DBAction actionEXT601 = database.table("EXT601").index("00").selectAllFields().build()
      DBContainer containerEXT601 = actionEXT601.getContainer()
      containerEXT601.set("EXCONO", CONO)
      containerEXT601.set("EXTRNR", iTRNR)
      containerEXT601.set("EXPONR",iPONR as int)
      containerEXT601.set("EXBANO", iBANO)
      containerEXT601.set("EXCAMU", iCAMU)

      if (!actionEXT601.read(containerEXT601)) {
        mi.error("No re-class defined")
        return
      } else {
        mi.outData.put("TRNR", containerEXT601.get("EXTRNR").toString())
        mi.outData.put("PONR", containerEXT601.get("EXPONR").toString())
        mi.outData.put("BANO", containerEXT601.get("EXBANO").toString())
        mi.outData.put("CAMU", containerEXT601.get("EXCAMU").toString())
        mi.outData.put("NSTS", containerEXT601.get("EXNSTS").toString())
        mi.outData.put("STAT", containerEXT601.get("EXSTAT").toString())
        mi.write()
      }
    }
  }
}
