/**
 * README
 * This transaction will evaluate what would happen should backflush transactions
 * be report.
 *
 * Name: PMS050MI.RptReceiptPRE
 * Description: Check backflush status before allowing report receipt
 * Date	      Changed By            Description
 * 20230209	  JHAGLER               initial development
 */
public class PMS050MI_RptReceiptPRE extends ExtendM3Trigger {

  private final TransactionAPI transaction
  private final MICallerAPI miCaller


  PMS050MI_RptReceiptPRE(TransactionAPI transaction, MICallerAPI miCaller) {
    this.transaction = transaction
    this.miCaller = miCaller
  }

  void main() {

    String FACI = transaction.parameters.get("FACI").toString()
    String MFNO = transaction.parameters.get("MFNO").toString()
    double RPQA = transaction.parameters.get("RPQA") as double

    String status = getBackflushStatus(FACI, MFNO, RPQA)
    if (status != 'OK') {
      transaction.abortTransaction("RPQA", "PM06020", status)
    }

  }

  /**
   * Get backflush status from EXT050MI/GetBackflushSts
   * @param FACI
   * @param MFNO
   * @param RPQA
   * @return message
   */
  String getBackflushStatus(String FACI, String MFNO, double RPQA) {
    String status = null
    def params = [
      "FACI": FACI,
      "MFNO": MFNO,
      "RPQA": RPQA.toString()
    ]

    miCaller.call("EXT050MI", "GetBackflushSts", params, {Map<String, ?> resp ->
      status = resp.get("STAT").toString()
    })

    return status
  }


}
