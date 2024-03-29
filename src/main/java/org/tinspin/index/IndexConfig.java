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
	private boolean defensiveKeyCopy = true;

	protected IndexConfig(int dimensions) {
		this.dimensions = dimensions;
	}

	public static IndexConfig create(int dimensions) {
		return new IndexConfig(dimensions);
	}


	/**
	 * Number of dimensions.
	 * @param dimensions Number of dimensions of keys.
	 * @return this
	 */
	public IndexConfig setDimensions(int dimensions) {
		this.dimensions = dimensions;
		return this;
	}

	/**
	 * @param defensiveKeyCopy
	 * Defensive keys copying. If 'false', the kd-tree will store the passed in
	 * double[] keys internally (this reduces required memory).
	 * If 'true', the keys are copied in order to avoid accidental modification.
	 * The latter obviously requires more memory. Default is 'true'.
	 * <p>
	 * This setting works only for kd-trees.
	 * @return this
	 */
	public IndexConfig setDefensiveKeyCopy(boolean defensiveKeyCopy) {
		this.defensiveKeyCopy = defensiveKeyCopy;
		return this;
	}


	public int getDimensions() {
		return dimensions;
	}

	public boolean getDefensiveKeyCopy() {
		return defensiveKeyCopy;
	}
}
