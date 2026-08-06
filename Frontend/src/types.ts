export interface RankedPrediction {
  country: string;
  confidence: number;
  rank: number;
}

export interface PredictionResponse {
  resnet50: RankedPrediction[];
  clip: RankedPrediction[];
  ensemble: RankedPrediction[];
}

export interface UploadImageResponse {
  imageId: number;
  predictions: PredictionResponse;
}

export interface RandomGameImageResponse {
  sessionId: number;
  imageId: number;
}

export interface GuessResultResponse {
  sessionId: number;
  userGuess: string;
  modelGuess: string;
  actualCountry: string;
  correct: boolean;
}

export interface ErrorResponse {
  error: string;
}
