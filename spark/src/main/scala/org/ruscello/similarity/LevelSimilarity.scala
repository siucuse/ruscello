/*
 * ruscello: real time time series analytic  on big data streaming platform
 * Author: Pranab Ghosh
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.ruscello.similarity

import org.apache.spark.SparkConf
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.Seconds
import com.typesafe.config.ConfigFactory
import org.apache.spark.streaming.dstream.PairDStreamFunctions
import org.hoidla.window.SizeBoundWindow


object LevelSimilarity {
  /**
 * @param args
 */
def main(args: Array[String]) {
	val Array(master: String, configFile: String) = args.length match {
		case x: Int if x == 2 => args.take(2)
		case _ => throw new IllegalArgumentException("missing command line args")
	}
	
	//load configuration
	System.setProperty("config.file", configFile)
	val config = ConfigFactory.load()
	val batchDuration = config.getInt("batch.duration")
	
	
	val conf = new SparkConf().setMaster(master).setAppName("LevelSimilarity")
	val ssc = new StreamingContext(conf, Seconds(batchDuration))
	val brConf = ssc.sparkContext.broadcast(config)
	
	val source = config.getString("stream.source")
	
	val updateFunc = (values: Seq[String], state: Option[LevelCheckingWindow]) => {
	  
	  def getWindow() : LevelCheckingWindow = {
	    val windowSize = brConf.value.getInt("window.size")
	    val windowStep = brConf.value.getInt("window.step")
	    val levelThrehold = brConf.value.getInt("level.threshold")
	    val levelThresholdMargin = brConf.value.getInt("level.threshold.margin")
	    val checkingStrategy = brConf.value.getString("checking.strategy")
	      
	      
	    new LevelCheckingWindow(windowSize, windowStep, levelThrehold, 
	    		levelThresholdMargin, checkingStrategy)
	  }
	  
	  def extractReading(line : String) : Int = {
	    val items = line.split(",")
	    val readingOrdinal = brConf.value.getInt("reading.ordinal")
	    Integer.parseInt(items(readingOrdinal))
	  }
	  
	  val window= state.getOrElse(getWindow)
	  values.foreach(l => {
	    window.addAndCheck(extractReading(l))
	  	}
	  )
	  
			 
	}
	//ssc.sparkContext.
	source match {
	  case "hdfs" => {
	    val path = config.getString("hdfs.path")
	    val idOrdinal = config.getInt("id.ordinal")
	    val lines = ssc.textFileStream(path)
	    
	    //map with id as the key
	    val keyedLines = lines.map { line =>
	      val items = line.split(",")
	      val id = items(idOrdinal)
	      (id, line)
	    }
	    
	    val pairStream = new PairDStreamFunctions(keyedLines)
	    
	    
	  }
	  
	  case "kafka" => {
	    
	  }
	  
	  case _ => {
	    throw new IllegalArgumentException("unsupported input stream source")
	  }
	}

	
	// start our streaming context and wait for it to "finish"
	ssc.start()
	
	// Wait for 10 seconds then exit. To run forever call without a timeout
	ssc.awaitTermination(10000)
	ssc.stop()	
  }
}