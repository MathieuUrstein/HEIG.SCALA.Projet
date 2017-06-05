package utils

import java.text.SimpleDateFormat

object Const {
  final val DbName = "piggybank"
  final val ValueStoredJWT = "userEmail"
  final val CreatedEntityHeaderName = "Location"
  final val format = new SimpleDateFormat("yyyy-MM-dd")


  final val SQLiteUniqueConstraintErrorCode = 2067
}
