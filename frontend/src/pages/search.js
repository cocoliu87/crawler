import Head from "next/head";
import { Fragment, useEffect } from "react";
import { Layout as DashboardLayout } from "src/layouts/dashboard/layout";
import {
  Box,
  Button,
  Container,
  Stack,
  Typography,
  Card,
  InputAdornment,
  OutlinedInput,
  SvgIcon,
} from "@mui/material";
import MagnifyingGlassIcon from "@heroicons/react/24/solid/MagnifyingGlassIcon";
import Item from "../components/Item";
import { FallingLines } from 'react-loader-spinner';
import { useState } from "react";
import axios from "axios";


const Page = () => {
  const [searchResults, setSearchResults] = useState([]);
  const [searchString, setSearchString] = useState("");
  const [all, setAll] = useState([]);
  const [currentList, setCurrentList] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemPerPage, setItemPerPage] = useState(5);
  const [isLoading, setIsLoading] = useState(false);
  const [count, setCount] = useState(0);
  const [totalResults, setTotalResults] = useState(0);

  useEffect(() => {
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  const searchInputChangeHandler = (event) => {
    const input = event.target.value;
    setSearchString(input);
  };

  const handleScroll = () => {
    const { scrollTop, clientHeight, scrollHeight } = document.documentElement;
    console.log("scrollTop + clientHeight ", scrollTop + clientHeight);
    console.log("scrollHeight ", scrollHeight);
    if (scrollTop + clientHeight >= scrollHeight - 20) {
      setCurrentPage((prev) => prev + 1);
    }
  };

  useEffect(() => {
    search();
    // console.log(currentPage)
  }, [currentPage]);

  function search() {
    setAll((prev) => []);
    setCurrentList((prev) => []);
    setIsLoading(true);

    // replace with the real search API
    // fetch the whole data

    const serch_api = `http://localhost:8081/api/search`;
    axios({
      // Endpoint to send files
      url: serch_api,
      method: "GET",
      params: {
          query: searchString,
          page: currentPage,
      }
    })
      // Handle the response from backend here
      .then((res) => {
        setTotalResults(res?.data?.total ?? 0);
        setSearchResults([...searchResults, ...res?.data?.results]);
        setIsLoading(false);
      })

      // Catch errors if any
      .catch((err) => {});
  };

  return (
    <Fragment>
      <Head>
        <title>Search | Devias Kit</title>
      </Head>
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          py: 8,
        }}
      >
        <Container maxWidth="xl">
          <Stack spacing={3}>
            <Stack direction="row" justifyContent="space-between" spacing={4}>
              <Stack spacing={1}>
                <Typography variant="h4">Search</Typography>
              </Stack>
            </Stack>
            <Card sx={{ p: 2 }}>
              <OutlinedInput
                defaultValue=""
                fullWidth
                placeholder="Type to search information from crawler"
                onChange={searchInputChangeHandler}
                startAdornment={
                  <InputAdornment position="start">
                    <SvgIcon color="action" fontSize="small">
                      <MagnifyingGlassIcon />
                    </SvgIcon>
                  </InputAdornment>
                }
                sx={{ maxWidth: 500 }}
              />
              <Button variant="contained" style={{ margin: "10px" }} onClick={search}>
                Search
              </Button>
            </Card>
          </Stack>
        </Container>

        {isLoading && <div style={{color: "black", position: "relative", left: "calc(50% - 80px)", top: "calc(40% - 80px)"}}>
          <FallingLines
            color="#6466f1"
            width="100"
            visible={true}
            ariaLabel='falling-lines-loading'
          />
        </div>}
        {!isLoading && <Container maxWidth="xl">
          {totalResults > 0 && <p>Total Results: {totalResults}</p>}
          {currentPage > 0 && <p>currentPage: {currentPage}</p>}
          {(searchResults ?? []).map((item, index) => (
            <Item
              key={item.id}
              index={index}
              id={item.id}
              url={item?.url}
              text={item?.text}
              pagerank={item?.pagerank}
              cosineSimiliarty={item?.cosineSimiliarty}
              score={item?.score}
            />
          ))}
          {(searchResults ?? []).length === 0 && <div>No results, please enter search keywords.</div>}
        </Container>}
      </Box>
    </Fragment>
  );
};

Page.getLayout = (page) => <DashboardLayout>{page}</DashboardLayout>;

export default Page;
