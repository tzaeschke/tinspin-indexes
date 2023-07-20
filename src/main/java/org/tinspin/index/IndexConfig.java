/*
 * Copyright 2016 Tilmann Zaeschke
 * Modification Copyright 2017 Christophe Schmaltz
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinspin.index;

public class IndexConfig {
    private int dimensions = 3;

	boolean defensiveKeyCopy = true;

	IndexConfig(int dimensions) {
		this.dimensions = dimensions;
	}

	/**
	 * Number of dimensions.
	 * @param dimensions Number of dimensions of keys.
	 */
	public void setDimensions(int dimensions) {
		this.dimensions = dimensions;
	}

	/**
	 * @param defensiveKeyCopy
	 * Defensive keys copying. If `false`, the kd-tree will store the passed in
	 * double[] keys internally (this reduces required memory).
	 * If `true`, the keys are copied in order to avoid accidental modification.
	 * The latter obviously requires more memory.
	 * <p>
	 * This setting works only for kd-trees.
	 */
	public void setDefensiveKeyCopy(boolean defensiveKeyCopy) {
		this.defensiveKeyCopy = defensiveKeyCopy;
	}


	public int getDimensions() {
		return dimensions;
	}

	public boolean getDefensiveKeyCopy() {
		return defensiveKeyCopy;
	}
}
