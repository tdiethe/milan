package com.amazon.milan.lang

import com.amazon.milan.program._
import com.amazon.milan.test.{IntKeyValueRecord, KeyValueRecord}
import com.amazon.milan.typeutil.{FieldDescriptor, TypeDescriptor, types}
import org.junit.Assert._
import org.junit.Test


object TestJoin {
  def getKey(r: KeyValueRecord): String = r.key

  def getValue(r: KeyValueRecord): String = r.value

  def joinRecords(left: KeyValueRecord, right: KeyValueRecord) = KeyValueRecord(left.key, left.value + ", " + right.value)

  def combineValues(key: String, leftValue: String, rightValue: String) = KeyValueRecord(key, leftValue + "." + rightValue)
}


@Test
class TestJoin {
  @Test
  def test_Join_WithTwoObjectStreams_ReturnsStreamWithExpectedNodeValues(): Unit = {
    val left = Stream.of[KeyValueRecord]
    val right = Stream.of[KeyValueRecord]

    val join = left.fullJoin(right)

    assertEquals(JoinType.FullEnrichmentJoin, join.joinType)
    assertEquals(left.node, join.leftInput)
    assertEquals(right.node, join.rightInput)
  }

  @Test
  def test_Join_ThenWhere_WithTwoObjectStreams_ReturnsStreamWithExpectedNodeValues(): Unit = {
    val left = Stream.of[KeyValueRecord]
    val right = Stream.of[KeyValueRecord]

    val join = left.fullJoin(right)
    val where = join.where((l, r) => l.key == r.key)

    val FullJoin(leftInput, rightInput, condition) = where.node.getExpression
    assertEquals(left.node.getExpression, leftInput)
    assertEquals(right.node.getExpression, rightInput)

    // Extract out the join condition expression.
    val FunctionDef(_, Equals(SelectField(SelectTerm("l"), "key"), SelectField(SelectTerm("r"), "key"))) = condition
  }

  @Test
  def test_Join_ThenWhere_ThenSelect_ToObjectStream_WithTwoObjectStreams_ReturnsStreamWithExpectedNodeValues(): Unit = {
    val left = Stream.of[KeyValueRecord]
    val right = Stream.of[KeyValueRecord]

    val join = left.fullJoin(right)
    val where = join.where((l, r) => l.key == r.key)
    val select = where.select((l, r) => TestJoin.joinRecords(l, r))

    val mapExpr = select.node.getExpression.asInstanceOf[MapRecord]
    assertEquals(where.node.getExpression, mapExpr.source)

    val FunctionDef(List("l", "r"), ApplyFunction(FunctionReference(objectTypeName, "joinRecords"), List(SelectTerm("l"), SelectTerm("r")), _)) = mapExpr.expr
    assertEquals(classOf[TestJoin].getName, objectTypeName)
  }

  @Test
  def test_Join_ThenWhere_ThenSelect_ToTupleStream_WithTwoObjectStreams_ReturnsStreamWithExpectedNodeValues(): Unit = {
    val left = Stream.of[KeyValueRecord]
    val right = Stream.of[KeyValueRecord]

    val join = left.fullJoin(right)
    val where = join.where((l, r) => l.key == r.key)
    val select = where.select(
      ((l: KeyValueRecord, r: KeyValueRecord) => TestJoin.joinRecords(l, r)) as "j")

    val mapExpr = select.node.getExpression.asInstanceOf[MapFields]
    assertEquals(where.node.getExpression, mapExpr.source)

    val List(FieldDefinition("j", FunctionDef(List("l", "r"), ApplyFunction(FunctionReference(objectTypeName, "joinRecords"), List(SelectTerm("l"), SelectTerm("r")), _)))) = mapExpr.fields
    assertEquals(classOf[TestJoin].getName, objectTypeName)
  }

  @Test
  def test_Join_ThenWhere_ThenSelectAll_WithTwoObjectStreams_ReturnsStreamWithExpectedFieldsAndMapExpression(): Unit = {
    val left = Stream.of[IntKeyValueRecord]
    val right = Stream.of[IntKeyValueRecord]
    val joined = left.fullJoin(right).where((l, r) => l.key == r.key)
    val output = joined.selectAll()

    val expectedFields = List(
      FieldDescriptor("left", TypeDescriptor.of[IntKeyValueRecord]),
      FieldDescriptor("right", TypeDescriptor.of[IntKeyValueRecord]))
    assertEquals(expectedFields, output.fields)

    val MapFields(_, fields) = output.node.getExpression
    val FieldDefinition("left", FunctionDef(List("l", "r"), SelectTerm("l"))) = fields.head
    val FieldDefinition("right", FunctionDef(List("l", "r"), SelectTerm("r"))) = fields.last
  }

  @Test
  def test_Join_ThenWhere_ThenSelectAll_WithObjectAndTupleStreams_ReturnsStreamWithExpectedFieldsAndMapExpression(): Unit = {
    val left = Stream.of[IntKeyValueRecord]
    val right = Stream.ofFields[(Int, Long)]("a", "b")
    val joined = left.fullJoin(right).where((l, r) => r match {
      case (a, b) => l.key == a
    })
    val output = joined.selectAll()

    val expectedFields = List(
      FieldDescriptor("left", TypeDescriptor.of[IntKeyValueRecord]),
      FieldDescriptor("a", types.Int),
      FieldDescriptor("b", types.Long))
    assertEquals(expectedFields, output.fields)

    val MapFields(_, fields) = output.node.getExpression
    val FieldDefinition("left", FunctionDef(List("l", "r"), SelectTerm("l"))) = fields.head
    val FieldDefinition("a", FunctionDef(List("l", "r"), SelectField(SelectTerm("r"), "a"))) = fields(1)
    val FieldDefinition("b", FunctionDef(List("l", "r"), SelectField(SelectTerm("r"), "b"))) = fields(2)
  }

  @Test
  def test_Join_ThenWhere_ThenSelectAll_WithTupleAndObjectStreams_ReturnsStreamWithExpectedFieldsAndMapExpression(): Unit = {
    val left = Stream.ofFields[(Int, Long)]("a", "b")
    val right = Stream.of[IntKeyValueRecord]
    val joined = left.fullJoin(right).where((l, r) => l match {
      case (a, b) => r.key == a
    })
    val output = joined.selectAll()

    val expectedFields = List(
      FieldDescriptor("a", types.Int),
      FieldDescriptor("b", types.Long),
      FieldDescriptor("right", TypeDescriptor.of[IntKeyValueRecord]))
    assertEquals(expectedFields, output.fields)

    val MapFields(_, fields) = output.node.getExpression
    val FieldDefinition("a", FunctionDef(List("l", "r"), SelectField(SelectTerm("l"), "a"))) = fields.head
    val FieldDefinition("b", FunctionDef(List("l", "r"), SelectField(SelectTerm("l"), "b"))) = fields(1)
    val FieldDefinition("right", FunctionDef(List("l", "r"), SelectTerm("r"))) = fields(2)
  }

  @Test
  def test_Join_ThenWhere_ThenSelectAll_WithTwoTupleStreams_ReturnsStreamWithExpectedFieldsAndMapExpression(): Unit = {
    val left = Stream.ofFields[(Int, Long)]("a", "b")
    val right = Stream.ofFields[(Int, Double)]("c", "d")
    val joined = left.fullJoin(right).where((l, r) => l match {
      case (a, b) => r match {
        case (c, d) => a == c
      }
    })
    val output = joined.selectAll()

    val expectedFields = List(
      FieldDescriptor("a", types.Int),
      FieldDescriptor("b", types.Long),
      FieldDescriptor("c", types.Int),
      FieldDescriptor("d", types.Double))
    assertEquals(expectedFields, output.fields)

    val MapFields(_, fields) = output.node.getExpression
    val FieldDefinition("a", FunctionDef(List("l", "r"), SelectField(SelectTerm("l"), "a"))) = fields.head
    val FieldDefinition("b", FunctionDef(List("l", "r"), SelectField(SelectTerm("l"), "b"))) = fields(1)
    val FieldDefinition("c", FunctionDef(List("l", "r"), SelectField(SelectTerm("r"), "c"))) = fields(2)
    val FieldDefinition("d", FunctionDef(List("l", "r"), SelectField(SelectTerm("r"), "d"))) = fields(3)
  }
}
