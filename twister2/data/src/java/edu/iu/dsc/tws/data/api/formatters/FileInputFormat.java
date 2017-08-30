//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
package edu.iu.dsc.tws.data.api.formatters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.data.api.InputFormat;
import edu.iu.dsc.tws.data.fs.FileInputSplit;
import edu.iu.dsc.tws.data.fs.FileStatus;
import edu.iu.dsc.tws.data.fs.FileSystem;
import edu.iu.dsc.tws.data.fs.Path;
import edu.iu.dsc.tws.data.fs.io.InputSplitAssigner;

/**
 * Base class for File input formats for specific file types the methods
 * {@link #nextRecord(Object)} and {@link #reachedEnd()} methods need to be implemented.
 * @param <OT>
 */
public class FileInputFormat<OT> implements InputFormat<OT , FileInputSplit> {
  private static final Logger LOG = Logger.getLogger(FileInputFormat.class.getName());

  /**
   * The desired number of splits, as set by the configure() method.
   */
  protected int numSplits = -1;

  /**
   * The path to the file that contains the input.
   */
  protected Path filePath;

  @Override
  public void configure(Config parameters) {

  }
  /**
   * Computes the input splits for the file. By default, one file block is one split. If more splits
   * are requested than blocks are available, then a split may be a fraction of a block and splits may cross
   * block boundaries.
   *
   * @param minNumSplits The minimum desired number of file splits.
   * @return The computed file splits.
   */
  @Override
  public FileInputSplit[] createInputSplits(int minNumSplits) throws Exception {
    if (minNumSplits < 1) {
      throw new IllegalArgumentException("Number of input splits has to be at least 1.");
    }

    // take the desired number of splits into account
    minNumSplits = Math.max(minNumSplits, this.numSplits);

    final Path path = this.filePath;
    final List<FileInputSplit> inputSplits = new ArrayList<FileInputSplit>(minNumSplits);

    // get all the files that are involved in the splits
    List<FileStatus> files = new ArrayList<FileStatus>();
    long totalLength = 0;

    final FileSystem fs = path.getFileSystem();
   // final FileStatus pathFile = fs.getFileStatus(path);

//    if (pathFile.isDir()) {
//      totalLength += addFilesInDir(path, files, true);
//    } else {
//      //testForUnsplittable(pathFile);
//
//      files.add(pathFile);
//      totalLength += pathFile.getLen();
//    }
    return new FileInputSplit[0];
  }

  @Override
  public InputSplitAssigner getInputSplitAssigner(FileInputSplit[] inputSplits) {
    return null;
  }

  @Override
  public void open(FileInputSplit split) throws IOException {

  }


  @Override
  public boolean reachedEnd() throws IOException {
    return false;
  }

  @Override
  public Object nextRecord(Object reuse) throws IOException {
    return null;
  }

  @Override
  public void close() throws IOException {

  }
}