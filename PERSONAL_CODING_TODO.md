# Non-AI coding work to-do list
A to-do list of features to implement without the use of AI.

## Issues

### UI
- [x] Standardized status messages - a message for every error code
- [x] Further restrictions for loan extension within UI 
  - ~~-Error indication in input modal similar to "Add Book/Member" modal errors~~
  - ~~-Adding GET request for /extend used when extension modal is opened to receive number of days able to extend~~
  - ~~-Should be an error modal if possible extension days is 0~~
  - [x] Change /books endpoint to also return original due dates
  - [x] Frontend functionality to calculate how many days can be extended based on difference between current due date and original due date
  - [x] "Extend loan" button does not appear when max extension days is reached
  - [x] Days input should reflect the response from the API
  - [x] There should also be information on the modal stating to the user how far they can extend the due date
- [x] Fix visuals of added UI features
  - [x] Overdue books container text too light
  - [x] Member summary
    - [x] Bug: "Loading member summary..." message does not disappear before selection of different member - /summary is not queried at initialization of site
    - [x] Member name and ID are not displayed in \<h3> container above loans and reservations
    - [x] Member summary container text too light
- [ ] Currently no way to test overdue book functionalities - should add debugging possibilities

## Finished steps

### 27 December 2025

- Added MAX_RESERVATION_REACHED translation to i18n.ts to fix error message format in status container.
- Removed \<h3> container in memberSummary ng-container (member identification display), as the member name and id were 
not queried correctly (queried from API response which does not have member id and name fields), leaving the text
display empty, and the information is already displayed in the drop-down above, making the \<h3> display redundant.

### 28 December 2025
- Made "Extend loan" function more intuitive
  - The API now also returns the original due date for loaned books
  - The frontend now calculates the difference between the current due date and the original due date and hides the 
"Extend loan" button if the difference is equal to the maximum allowed extension days (90 days)
  - The input field does not go above the amount of days remaining on extension
  - The default day input in the extension modal is 1, instead of 7.
  - Added disclaimer to extension modal stating that the maximum extension days is 90 days.
- Fixed "Loading member summary..." bug by adding `this.loadMemberSummary(this.selectedMemberId)` call to refreshAll() function.
- Changed CSS for "Overdue" and "Member Summary" sections to have darker backgrounds, allowing for better readability of 
light text.
