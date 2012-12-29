package io.viper.examples

import org.clapper.classutil.{ClassFinder, ClassInfo}
import collection.mutable.ListBuffer
import io.viper.examples.FileWalker.FileHandler
import java.io.File
import collection.mutable
import io.viper.common.{Response, VirtualServer, MultiHostServer}

// curl -H 'Host: helloworld.com:8080' http://localhost:8080/hello
class DynamicHelloWorld extends VirtualServer("helloworld.com") {
  get("/hello", { args => Response("world") })
}

object DynamicContainer extends MultiHostServer(8080) {
  DynamicLoader.load(".").map(_.name).foreach { name =>
    route(Class.forName(name).newInstance().asInstanceOf[VirtualServer])
  }
}

object DynamicLoader {
  def load(path: String): List[ClassInfo] = {
    val files = new ListBuffer[File]
    FileWalker.enumerateFolders(path, new FileHandler {
      def process(file: File) {
        if (file.getName.endsWith(".jar")) {
          files.append(file)
        }
      }
    })
    load(files.toList)
  }

  def load(jarFiles: List[File]): List[ClassInfo] = {
    val finder = ClassFinder(jarFiles)
    val classesMap = ClassFinder.classInfoMap(finder.getClasses())
    val plugins = ClassFinder.concreteSubclasses("io.viper.common.VirtualServer", classesMap).toList
    val filtered = plugins.filter(_.name != "io.viper.common.VirtualServer")
    filtered.foreach(println(_))
    filtered
  }
}

object FileWalker {
  def enumerateFolders(startFolder: String, handler: FileWalker.FileHandler) {
    val rootDir = new File(startFolder)
    if (!rootDir.exists) {
      throw new IllegalArgumentException("file does not exist: " + startFolder)
    }

    val stack = new mutable.Stack[File]
    stack.push(rootDir)

    while (!stack.isEmpty) {
      val curFile = stack.pop()
      val subFiles = curFile.listFiles
      if (subFiles != null) {
        val toPush = List() ++ subFiles.par.flatMap {
          file =>
            if (file.isDirectory) {
              if (handler.skipDir(file)) {
                Nil
              } else {
                List(file)
              }
            } else {
              handler.process(file)
              Nil
            }
        }
        toPush.foreach(stack.push)
      }
    }
  }

  trait FileHandler {
    def skipDir(file: File): Boolean = false
    def process(file: File)
  }
}