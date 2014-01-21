/*
 * Copyright 2014 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.ncd.passerelle.actors;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.beanutils.ConvertUtils;

import ptolemy.data.BooleanToken;
import ptolemy.data.IntMatrixToken;
import ptolemy.data.IntToken;
import ptolemy.data.expr.Parameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdProcessingObject;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.actor.v5.Actor;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.core.Port;
import com.isencia.passerelle.core.PortFactory;

public abstract class NcdAbstractDataTransformer extends Actor {

	private static final long serialVersionUID = -289682801810608304L;
	
	public Port input;
	public Port output;

	protected ReentrantLock lock;
	
	public Parameter isEnabled;
	public Parameter entryGroupParam, processingGroupParam;
	public Parameter framesParam, dimensionParam;
	
	protected boolean enabled;
	
	protected int dimension;
	protected long[] frames;
	protected int[] grid;
	protected int entryGroupID, processingGroupID;
	
	public NcdAbstractDataTransformer(CompositeEntity container, String name) throws IllegalActionException,
			NameDuplicationException {
		super(container, name);

		input = PortFactory.getInstance().createInputPort(this, "input", NcdProcessingObject.class);
		output = PortFactory.getInstance().createOutputPort(this, "result");

		isEnabled = new Parameter(this, "enable", new BooleanToken(false));

		dimensionParam = new Parameter(this, "dimensionParam", new IntToken(-1));
		framesParam = new Parameter(this, "framesParam", new IntMatrixToken());

		entryGroupParam = new Parameter(this, "entryGroupParam", new IntToken(-1));
		processingGroupParam = new Parameter(this, "processingGroupParam", new IntToken(-1));
	}
	
	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		
		try {
			enabled = ((BooleanToken) isEnabled.getToken()).booleanValue();
			if (!enabled) {
				return;
			}
			
			dimension = ((IntToken) dimensionParam.getToken()).intValue();
			
			entryGroupID = ((IntToken) entryGroupParam.getToken()).intValue();
			processingGroupID = ((IntToken) processingGroupParam.getToken()).intValue();
			
			int[][] framesMatrix = ((IntMatrixToken) framesParam.getToken()).intMatrix();
			if (framesMatrix == null || framesMatrix.length != 1) {
				throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Invalid data shape", this, null);
			}
			frames = (long[]) ConvertUtils.convert(framesMatrix[0], long[].class);
			
			int gridDimension = frames.length - dimension;
			if (gridDimension < 0) {
				throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Data shape incompatible with the specified dimension", this, null);
			}
			if (gridDimension > 0) {
				grid = Arrays.copyOf(framesMatrix[0], gridDimension);
			} else {
				grid = null;
			}
		} catch (IllegalActionException e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing NCD actor",
					this, e);
		}
	}

}
