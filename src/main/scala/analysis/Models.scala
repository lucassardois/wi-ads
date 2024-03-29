package analysis

import org.apache.spark.ml.classification.{DecisionTreeClassifier, LogisticRegression, NaiveBayes}
import org.apache.spark.ml.feature._
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.sql.DataFrame

object Models {

  def naiveBayesClassifier(train: DataFrame): PipelineModel = {

    // Get ColumnIndexer
    val arrayIndexer = indexerColumn(train.columns.toList.filter(x => x != "weights").filter(x => x != "label"))
    val allColumns = arrayIndexer.map(_.getOutputCol)

    val labelIndexer = new StringIndexer().setInputCol("label").setOutputCol("labelIndexer")

    val assembler = new VectorAssembler().setInputCols(allColumns).setOutputCol("features")

    val naive = new NaiveBayes().setSmoothing(1.0).setModelType("multinomial").setLabelCol("label").setFeaturesCol("features").setWeightCol("weights")

    val pipeline = new Pipeline().setStages(arrayIndexer ++ Array(labelIndexer, assembler, naive))

    val pipelineModel = pipeline.fit(train)


    pipelineModel.write.overwrite().save("models/NBC")
    println("[TheIllusionists] Model saved in the models/NBC folder !")

    pipelineModel

  }

  def logiscticTest(train: DataFrame): PipelineModel = {


    // Get ColumnIndexer
    val arrayIndexer = indexerColumn(train.columns.toList.filter(x => x != "weights").filter(x => x != "label").filter(x => x != "interests"))
    val allColumns = arrayIndexer.map(_.getOutputCol)

    val arrayEncoder = encoderColumn(allColumns)
    val allColumnsEncoder = arrayEncoder.map(_.getOutputCols(0))


    val labelIndexer = new StringIndexer().setInputCol("label").setOutputCol("labelIndexer")

    val assembler = new VectorAssembler().setInputCols(allColumnsEncoder).setOutputCol("features")

    val logisticR = new LogisticRegression()
        .setFeaturesCol("features")
        .setLabelCol("label")
        .setMaxIter(1000)
        .setWeightCol("weights")
        //.setAggregationDepth(10)
        .setThreshold(0.4955)
        .setRegParam(0.0275)
        //.setElasticNetParam(2)

    val pipeline = new Pipeline().setStages(arrayIndexer ++ arrayEncoder ++ Array(labelIndexer, assembler, logisticR))

    val pipelineModel = pipeline.fit(train)

    pipelineModel.write.overwrite().save("models/LR")
    println("[TheIllusionists] Model saved in the models/LR folder !")

    pipelineModel
  }

  def encoderColumn(listNameColumn: Array[String]): Array[OneHotEncoderEstimator] = {
    val listEncoder = listNameColumn.map(x => {
      new OneHotEncoderEstimator().setInputCols(Array(x)).setOutputCols(Array(x + "VC")).setHandleInvalid("keep")
    })
    listEncoder
  }

  def logisticReg(train: DataFrame): PipelineModel = {

    // Get ColumnIndexer
    val arrayIndexer = indexerColumn(train.columns.toList.filter(x => x != "weights"))
    val allColumns = arrayIndexer.map(_.getOutputCol).filter(x => x != "labelIndexer")

    val assembler = new VectorAssembler()
      .setInputCols(allColumns)
      .setOutputCol("rawFeatures")

    //vector slicer
    val slicer = new VectorSlicer().setInputCol("rawFeatures").setOutputCol("slicedFeatures").setNames(allColumns)

    //scale the features
    val scaler = new StandardScaler().setInputCol("slicedFeatures").setOutputCol("features").setWithStd(true).setWithMean(true)

    //label for binaryClassifier
    val binarizerClassifier = new Binarizer().setInputCol("labelIndexer").setOutputCol("binaryLabel")

    //logistic regression
    val logisticRegression = new LogisticRegression()
        .setWeightCol("weights")
        .setMaxIter(1000)
        .setRegParam(0.3)
        //.setElasticNetParam(0.8)
        .setLabelCol("binaryLabel")
        .setFeaturesCol("features")

    //Chain indexers and tree in a Pipeline
    val lrPipeline = new Pipeline().setStages(arrayIndexer ++ Array(assembler, slicer, scaler, binarizerClassifier, logisticRegression))

    // Train model
    val lrModel = lrPipeline.fit(train)

    lrModel
  }

  def decisionTree(train: DataFrame): PipelineModel = {

    // Get ColumnIndexer
    val arrayIndexer = indexerColumn(train.columns.toList)

    val allColumns = arrayIndexer.map(_.getOutputCol)

    val assembler = new VectorAssembler()
      .setInputCols(allColumns)
      .setOutputCol("rawFeatures")

    //index category index in raw feature
    val indexer = new VectorIndexer().setInputCol("rawFeatures").setOutputCol("rawFeaturesIndexed").setMaxCategories(10)
    //PCA
    val pca = new PCA().setInputCol("rawFeaturesIndexed").setOutputCol("features").setK(10)
    //label for multi class classifier
    val bucketizer = new Bucketizer().setInputCol("label").setOutputCol("multiClassLabel").setSplits(Array(Double.NegativeInfinity, 0.0, 15.0, Double.PositiveInfinity))

    // Train a DecisionTree model.
    val dt = new DecisionTreeClassifier().setLabelCol("label").setFeaturesCol("features")

    // Chain all into a Pipeline
    val dtPipeline = new Pipeline().setStages(arrayIndexer ++ Array(assembler, indexer, pca, bucketizer, dt))

    // Train model.
    val dtModel = dtPipeline.fit(train)

    dtModel
  }

  def indexerColumn(listNameColumn: List[String]): Array[StringIndexer] = {
    val listIndexer = listNameColumn.map(x => {
      new StringIndexer().setInputCol(x).setOutputCol(x + "Indexer").setHandleInvalid("keep")
    })
    listIndexer.toArray
  }
}
