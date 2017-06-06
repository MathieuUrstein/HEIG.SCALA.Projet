package utils

import java.text.SimpleDateFormat

object Const {
  final val DbName = "piggybank"
  final val ValueStoredJWT = "userEmail"
  final val CreatedEntityHeaderName = "Location"
  final val format = new SimpleDateFormat("yyyy-MM-dd")
  final val errorMessageEmptyStringJSON = "validate.error.empty.value"
  final val errorMessageEmptyArrayJSON = "validate.error.empty.array"


  final val SQLiteUniqueConstraintErrorCode = 2067
}
