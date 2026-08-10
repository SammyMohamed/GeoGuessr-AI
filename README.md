# GeoGuessr AI

This project is a web application that guesses the country for a given street view photo. 

Try it live!

https://geoguessr-ai-frontend.onrender.com/

## How it works

This will utilize a model I built for a computer vision course that achieved accuracy of 77.7% and had the correct country in its top 5 choices 95.4% of the time among the 56 countries for which I had sufficient data. 

## Proposed structure (subject to change)

- **`Model/`** — Python inference service. Wraps the trained
  CLIP + ResNet-50 ensemble behind a single `/predict` endpoint. 
  Model weights are trained and evaluated separately and loaded once 
  at service startup.
- **`Backend/`** — Kotlin service (Ktor) that handles image uploads, calls
  the inference service, and persists results and game sessions to a
  relational database.
- **`Frontend/`** — React + TypeScript client for uploading images,
  viewing predictions, and playing the guessing game.
