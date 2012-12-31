package io.viper.common

import collection.mutable.ListBuffer
import java.io.File
import collection.mutable
import org.clapper.classutil.{ClassFinder, ClassInfo}
import io.viper.common.FileWalker.FileHandler
import java.net.URLClassLoader

class DynamicContainerApp(port: Int = 80, path: String = ".") extends MultiHostServerApp(8080) {
  DynamicLoader.load(path).map(_.name).foreach { name =>
    val runner = Class.forName(name).newInstance().asInstanceOf[VirtualServerRunner]
    runner.start
    route(runner.hostname, runner.create)
  }
}

class DynamicContainer(port: Int = 80, path: String = ".") extends MultiHostServer(port) {

  def loadClass[T](jar: File, name: String): T = {
    val child = new URLClassLoader(Array(jar.toURL()), this.getClass().getClassLoader())
    Class.forName(name, true, child).newInstance.asInstanceOf[T]
  }

  override def run {
    DynamicLoader.load(path).foreach { info =>
      println("loading location: %s, name: %s".format(info.location, info.name))
      val runner = loadClass[VirtualServerRunner](info.location, info.name)
      println("adding: %s".format(runner.hostname))
      route(runner)
    }
    super.run
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
    val plugins = ClassFinder.concreteSubclasses("io.viper.common.VirtualServerRunner", classesMap).toList
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