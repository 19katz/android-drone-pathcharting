# android-drone-pathcharting
This is a project that I implemented during my freshman year. I received a Synopsys Science Fair First Prize for it. It is an Android app that uses the Google Maps API and cubic spline interpolation to let the user select and drag points on a map for the drones to fly through and interactively fits a smooth path through these points. 

## Getting Started

This project is meant to be run in Android Studio. Navigate to the file MapsActivity.java in order to run the app:

'''
app > src > main > java > com > example > katherinezhang > dronepathcharting
'''
## Running

Run MapsActivity.java and select the emulator or device on which the app should be run. 

## Using the App

Once the user is satisfied with the path, the GPS coordinates are saved to a file and then uploaded through a different app to the drone controller, and then the flight of the drone through this path can be performed automatically from takeoff to landing. This is very useful for tasks such as coastal patrol and surveying disaster-stricken areas. The smoothness of the path is important to the quality of the footage taken by the drone. This project can be imported into Android Studio, but to run it, a Google Maps API key must be obtained and placed in the google_maps_api.xml file.
