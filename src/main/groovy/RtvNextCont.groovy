import java.text.DecimalFormat
import java.text.SimpleDateFormat

public class RtvNextCont extends ExtendM3Transaction {
  private final MIAPI mi;
  private final ProgramAPI program;
  private final MICallerAPI miCaller;

  private int CONO

  public RtvNextCont(MIAPI mi, ProgramAPI program, MICallerAPI miCaller) {
    this.mi = mi;
    this.program = program;
    this.miCaller = miCaller
  }

  public void main() {

    CONO = program.LDAZD.CONO as int
    String WHLO = mi.inData.get("WHLO").toString()

    if (WHLO.length() != 3) {
      mi.error("Warehouse is required and must be 3 characters long.")
      return;
    }



    String m3Date = getWarehouseDate(WHLO)
    if (m3Date == "") {
      mi.error("Unable to retrieve current date for warehouse ${WHLO}.")
      return;
    }
    String julianDate = convertToM3ToJulian(m3Date)
    if (julianDate == "") {
      mi.error("Unable to retrieve current julian date for warehouse ${WHLO}.")
      return;
    }


    HashMap<String, String> series = getContainerNumberSeries(WHLO)
    if (series.size() != 2) {
      mi.error("Unable to retrieve number series for warehouse ${WHLO}.")
      return;
    }
    String seriesType = series.get("NBTY")
    String seriesNumber = series.get("NBID")
    if (seriesType == "" || seriesNumber == "") {
      mi.error("Unable to retrieve number series for warehouse ${WHLO}.")
      return;
    }

    long nextNumber = getNextNumber(seriesType, seriesNumber)
    if (nextNumber == 0) {
      mi.error("Unable to retrieve next number from series ${seriesType}/${seriesNumber}")
      return;
    }
    String containerNumber = WHLO + julianDate + String.format("%06d", nextNumber)

    mi.outData.put("CAMU", containerNumber)
    mi.write()

  }

  long getNextNumber(String seriesType, String seriesNumber) {
    def params = [
      "DIVI": "",
      "NBTY": seriesType,
      "NBID": seriesNumber
    ]

    long nextNumber = 0
    miCaller.call("CRS165MI", "RtvNextNumber", params, { Map<String, String> resp ->
      if (!resp.error) {
        nextNumber = resp.get("NBNR") as Long
      }
    })
    return nextNumber
  }

  HashMap<String, String> getContainerNumberSeries(String WHLO) {
    def params = [
      "FILE": "MITWHL",
      "PK01": WHLO
    ]
    String seriesType = "";
    String series = "";
    miCaller.call("CUSEXTMI", "GetFieldValue", params, { Map<String, String> resp ->
      seriesType = resp.get("A330").toString()
      series = resp.get("A430").toString()
    })
    HashMap<String, String> data = new HashMap<String, String>()
    data.put("NBTY", seriesType.trim())
    data.put("NBID", series.trim())
    return data
  }

  String getWarehouseDate(String WHLO) {
    def params = [
      WHLO: WHLO
    ]
    String date = ""
    miCaller.call("DRS045MI", "GetWHLOData", params, { Map<String, String> resp ->
      date = resp.get("DATE").toString()
    })
    return date
  }

  String convertToM3ToJulian(String date) {
    SimpleDateFormat m3 = new SimpleDateFormat("yyyyMMdd")
    SimpleDateFormat julian = new SimpleDateFormat("yyDDD")
    return julian.format(m3.parse(date))
  }
}