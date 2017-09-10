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
import java.nio.charset.Charset;
import java.util.logging.Logger;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.data.api.InputFormat;
import edu.iu.dsc.tws.data.fs.Path;
import edu.iu.dsc.tws.data.fs.io.InputSplit;
import edu.iu.dsc.tws.data.fs.io.InputSplitAssigner;

/**
 * Created by pulasthi on 8/24/17.
 */
public class TextInputFormatter extends DelimitedInputFormat<String> {

  private static final Logger LOG = Logger.getLogger(TextInputFormatter.class.getName());

  private static final long serialVersionUID = 1L;

  /**
   * Code of \r, used to remove \r from a line when the line ends with \r\n.
   */
  private static final byte CARRIAGE_RETURN = (byte) '\r';

  /**
   * Code of \n, used to identify if \n is used as delimiter.
   */
  private static final byte NEW_LINE = (byte) '\n';

  public TextInputFormatter(Path filePath) {
    super(filePath, null);
  }

  @Override
  public String getCharsetName() {
    return charsetName;
  }

  @Override
  public void setCharsetName(String charsetName) {
    if (charsetName == null) {
      throw new IllegalArgumentException("Charset must not be null.");
    }
    this.charsetName = charsetName;
  }

  /**
   * The name of the charset to use for decoding.
   */
  private String charsetName = "UTF-8";

  @Override
  public void configure(Config parameters) {
    super.configure(parameters);

    if (charsetName == null || !Charset.isSupported(charsetName)) {
      throw new RuntimeException("Unsupported charset: " + charsetName);
    }
  }

  @Override
  public String readRecord(String reusable, byte[] bytes, int offset, int numBytes) throws IOException {
    //Check if \n is used as delimiter and the end of this line is a \r, then remove \r from the line
    if (this.getDelimiter() != null && this.getDelimiter().length == 1
        && this.getDelimiter()[0] == NEW_LINE && offset + numBytes >= 1
        && bytes[offset + numBytes - 1] == CARRIAGE_RETURN){
      numBytes -= 1;
    }

    return new String(bytes, offset, numBytes, this.charsetName);
  }

  @Override
  public String nextRecord(String reuse) throws IOException {
    return null;
  }
}
