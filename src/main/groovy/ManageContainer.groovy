/**
 * README
 * This utility supports common operations related to working with containers and
 * appended suffixes required to support the current container managed 7 configuration.
 *
 * Name: ManageContainer
 * Description: common container utility
 * Date	      Changed By            Description
 * 20230209	  JHAGLER               initial development
 * 20230215	  JHAGLER               allow better support of other container number formats
 * 20230217	  JHAGLER               changes requested from Infor
 */

class ManageContainer extends ExtendM3Utility {

  /**
   * Gets next container number in sequence
   * This search must be done across all warehouses
   * @param database
   * @param CONO
   * @param CAMU
   * @return nextContainerNumber
   */
  String GetNextContainerNumber(DatabaseAPI database, int CONO, String CAMU) {

    if (CAMU == null || CAMU.isEmpty()) {
      // container cannot be empty
      return null
    }

    // list the last balance ID matching the base container number
    String nextCAMU = null

    String baseCAMU = getBaseContainer(CAMU)
    ExpressionFactory exp = database.getExpressionFactory("MITLOC")
    exp = exp.like("MLCAMU",baseCAMU + "%")

    DBAction actionMITLOC = database.table("MITLOC")
      .index("99")  // sort by CONO, CAMU
      .matching(exp)
      .reverse()  // read in reverse order to get highest matching container number first
      .build()
    DBContainer containerMITLOC = actionMITLOC.createContainer()
    containerMITLOC.set("MLCONO", CONO)

    int keys = 1
    int limit = 1
    actionMITLOC.readAll(containerMITLOC, keys, limit, {DBContainer c ->
      String lastCAMU = c.getString("MLCAMU")
      String suffix = getSuffix(lastCAMU)
      int nextSuffix = 1
      if (suffix) {
        nextSuffix =  (suffix as int) + 1
      }
      nextCAMU = baseCAMU + "_" + String.format("%03d", nextSuffix)
    })

    return nextCAMU

  }


  /**
   * Select a specific balance ID from a given "base" container
   * @param database
   * @param CONO
   * @param WHLO
   * @param ITNO
   * @param WHSL
   * @param CAMU
   * @return balance id details CONO, WHLO, ITNO, WHSL, BANO, CAMU, STQT, ALQT, PLQT, STAS
   */
  Map<String, ?> SelectBalanceID(DatabaseAPI database, int CONO, String WHLO, String ITNO, String WHSL, String CAMU) {

    String baseContainer = getBaseContainer(CAMU)

    ExpressionFactory exp = database.getExpressionFactory("MITLOC")
    exp = exp.like("MLCAMU", baseContainer.trim() + "%")
    DBAction action = database.table("MITLOC").index("00").selection("MLSTQT", "MLALQT", "MLPLQT", "MLSTAS")
      .matching(exp).build()

    DBContainer container = action.getContainer()
    container.setInt("MLCONO", CONO)
    container.setString("MLWHLO", WHLO)
    container.setString("MLITNO", ITNO)
    container.setString("MLWHSL", WHSL)

    Map<String, ?> balanceId = null
    int keys = 4
    int limit = 1
    action.readAll(container, keys, limit, { DBContainer c ->
      balanceId = [
        "CONO": c.getInt("MLCONO"),
        "WHLO": c.get("MLWHLO").toString(),
        "ITNO": c.get("MLITNO").toString(),
        "WHSL": c.get("MLWHSL").toString(),
        "BANO": c.get("MLBANO").toString(),
        "CAMU": c.get("MLCAMU").toString(),
        "STQT": c.getDouble("MLSTQT"),
        "ALQT": c.getDouble("MLALQT"),
        "PLQT": c.getDouble("MLPLQT"),
        "STAS": c.get("MLSTAS").toString()
      ]
    })

    return balanceId

  }


  /**
   * Check to see if the given container is a valid "base container"
   * @param CAMU
   * @return isBase
   */
  boolean isBaseContainer(String CAMU) {
    boolean isBase = false
    // valid base containers have not _ suffix
    // e.g.  CA1209389320
    // valid containers with suffix have a _ suffix with a three char integer
    // e.g. CA1209389320_002

    if (CAMU == null || CAMU.isEmpty()) {
      return null
    }

    String suffix = getSuffix(CAMU)
    if (suffix == null) {
      // no valid suffix was found
      isBase = true
    }

    return isBase
  }

  /**
   * Returns a valid container suffix
   * Will only return a valid integer suffix or null
   * @param CAMU
   * @return
   */
  String getSuffix(String CAMU) {
    String suffix = null
    if (!CAMU.contains("_")) {
      return null
    }

    String[] elements = CAMU.split("_")
    if (elements.length == 2) {  // should have two elements
      String secondElement = elements[1]
      if (secondElement.length() == 3) {  // second element should be three chars long
        if (secondElement.isInteger()) {  // chars should be a valid integer
          suffix = secondElement
        }
      }
    }
    return suffix
  }


  /**
   * Get the base container portion from the given container number
   * @param CAMU
   * @return
   */
  String getBaseContainer(String CAMU) {
    String baseContainer = null
    if (CAMU.contains("_")) {
      baseContainer = CAMU.split("_")[0]
    } else {
      baseContainer = CAMU
    }
    return baseContainer
  }

}
